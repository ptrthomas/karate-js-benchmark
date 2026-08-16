#!/usr/bin/env bash
#
# The canonical benchmark run: a dedicated EC2 host instead of a GitHub-hosted runner.
#
# Why: shared CI runners are noisy (spikes, noisy neighbours) — two back-to-back runs have
# disagreed by 2.3x on a row neither build touched. A single-tenant on-demand instance of a
# PINNED type gives every run the same hardware class AND a quiet machine. The default,
# c6a.2xlarge, is 8 vCPU of EPYC Milan — the same CPU family as GitHub's EPYC 7763 runners,
# so the karate-vs-best RATIOS stay comparable with the archived `results/*-ci.csv` files
# (absolute ms are machine-specific; ratios cancel machine speed).
#
# 8 vCPU, not 4, and this is load-bearing: on a 4 vCPU host the JIT's compiler threads race
# the measured thread, and whether a hot path finishes compiling before or during a row's
# measurement window flips that row's number. Measured directly (2026-08-16, c6a.xlarge vs
# c6a.2xlarge, 3 runs each): at 4 vCPU the karate arm was bimodal — Functions swung 2.0x and
# Large-1KB 4.1x between runs while every Rhino cell stayed flat — and at 8 vCPU every
# karate spread collapsed to <=1.4x (most rows <=1.04x). GitHub's hosted runners are 4 vCPU,
# which is a second, independent reason their numbers were untrustworthy here.
#
# What it does, end to end (fully unattended, ~30-45 min, ~$0.10-0.15):
#   1. launches the instance (AL2023 x86_64, resolved fresh via SSM) with an ephemeral
#      ssh-only security group scoped to your current IP; everything tagged
#      karate-js-benchmark=true and terminated on exit — success OR failure
#   2. rsyncs your LOCAL karatelabs/karate checkout and this repo to the host — local,
#      not a GitHub clone, so unpushed engine work benchmarks correctly
#   3. verifies the karate.version pin, builds karate-js from source, runs the benchmark
#      BENCH_RUNS times (default 3)
#   4. copies all artifacts back to target/ec2/ and selects the MEDIAN run by the
#      geometric mean of karate_vs_best over the five fresh-context workload rows —
#      a whole run is selected, never per-cell medians, so the published block stays
#      internally consistent
#   5. prints the splice-and-archive commands for the README
#
# Usage — the minimal env is two variables (see etc/aws.env.example):
#   source /path/to/your/private/aws.env     # AWS_PROFILE + AWS_REGION suffice: the script
#                                            # then uses your default VPC's subnet and
#                                            # creates (and deletes) an ephemeral key pair
#   ./etc/ec2-benchmark.sh
#
# Knobs: BENCH_SUBNET (default: the default VPC's subnet), BENCH_KEY_NAME + BENCH_KEY_FILE
# (default: an ephemeral key pair created for the run and deleted after), BENCH_INSTANCE_TYPE
# (c6a.2xlarge), BENCH_RUNS (3), KARATE_SRC (auto-located like build.sh: ../karate then
# ../../karate). The karate-profiling KP_* variables are honored as fallbacks.
#
set -euo pipefail
cd "$(dirname "$0")/.."

: "${AWS_PROFILE:?source your aws env first}"
: "${AWS_REGION:?source your aws env first}"
SUBNET_ID=${BENCH_SUBNET:-${KP_SUBNET:-}}
KEY_NAME=${BENCH_KEY_NAME:-${KP_KEY_NAME:-}}
KEY_FILE=${BENCH_KEY_FILE:-${KP_KEY_FILE:-}}
INSTANCE_TYPE=${BENCH_INSTANCE_TYPE:-c6a.2xlarge}
RUNS=${BENCH_RUNS:-3}

AWS="aws --profile $AWS_PROFILE --region $AWS_REGION"

# locate the karate checkout exactly the way build.sh does
if [[ -z "${KARATE_SRC:-}" ]]; then
  for candidate in ../karate ../../karate; do
    [[ -f "$candidate/karate-js/pom.xml" ]] && KARATE_SRC=$candidate && break
  done
fi
[[ -n "${KARATE_SRC:-}" && -f "$KARATE_SRC/karate-js/pom.xml" ]] \
  || { echo "ERROR: no karate checkout found — set KARATE_SRC" >&2; exit 1; }
KARATE_SRC=$(cd "$KARATE_SRC" && pwd)
KARATE_SHA=$(git -C "$KARATE_SRC" rev-parse --short HEAD)
echo "==> karate source: $KARATE_SRC @ $KARATE_SHA"

# fail the version-pin check BEFORE spending a cent
SRC_VERSION=$(mvn -q -f "$KARATE_SRC/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)
OUR_VERSION=$(mvn -q help:evaluate -Dexpression=karate.version -DforceStdout)
[[ "$SRC_VERSION" == "$OUR_VERSION" ]] \
  || { echo "ERROR: karate main is $SRC_VERSION but pom.xml pins $OUR_VERSION — update the pom" >&2; exit 1; }

AMI=$($AWS ssm get-parameters --names /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
      --query 'Parameters[0].Value' --output text)

# no subnet given → use the account's default VPC (present in any untouched AWS account)
if [[ -z "$SUBNET_ID" ]]; then
  SUBNET_ID=$($AWS ec2 describe-subnets --filters Name=default-for-az,Values=true \
              --query 'Subnets[0].SubnetId' --output text)
  [[ "$SUBNET_ID" != "None" && -n "$SUBNET_ID" ]] \
    || { echo "ERROR: no default VPC in $AWS_REGION — set BENCH_SUBNET to a public subnet" >&2; exit 1; }
  echo "==> using default-VPC subnet $SUBNET_ID"
fi
VPC_ID=$($AWS ec2 describe-subnets --subnet-ids "$SUBNET_ID" --query 'Subnets[0].VpcId' --output text)
MYIP=$(curl -s https://checkip.amazonaws.com)

INSTANCE_ID=""
SG_ID=""
EPHEMERAL_KEY=""
cleanup() {
  set +e
  if [[ -n "$INSTANCE_ID" ]]; then
    echo "==> terminating $INSTANCE_ID"
    $AWS ec2 terminate-instances --instance-ids "$INSTANCE_ID" > /dev/null
    $AWS ec2 wait instance-terminated --instance-ids "$INSTANCE_ID"
  fi
  if [[ -n "$SG_ID" ]]; then
    $AWS ec2 delete-security-group --group-id "$SG_ID" && echo "==> deleted $SG_ID"
  fi
  if [[ -n "$EPHEMERAL_KEY" ]]; then
    $AWS ec2 delete-key-pair --key-name "$EPHEMERAL_KEY" && echo "==> deleted key pair $EPHEMERAL_KEY"
    rm -f "$KEY_FILE"
  fi
  # the independent check: an empty answer here means nothing is left costing money
  $AWS ec2 describe-instances \
    --filters Name=tag:karate-js-benchmark,Values=true \
              Name=instance-state-name,Values=pending,running,stopping,stopped \
    --query 'Reservations[].Instances[].InstanceId' --output text
}
trap cleanup EXIT

# no key pair given → create an ephemeral one, deleted (with its pem) by cleanup
if [[ -z "$KEY_NAME" ]]; then
  KEY_NAME="karate-js-benchmark-$$"
  KEY_FILE="target/$KEY_NAME.pem"
  mkdir -p target
  $AWS ec2 create-key-pair --key-name "$KEY_NAME" --key-type ed25519 \
       --query KeyMaterial --output text > "$KEY_FILE"
  chmod 600 "$KEY_FILE"
  EPHEMERAL_KEY="$KEY_NAME"
  echo "==> created ephemeral key pair $KEY_NAME"
elif [[ -z "$KEY_FILE" ]]; then
  echo "ERROR: BENCH_KEY_NAME is set but BENCH_KEY_FILE (its private key) is not" >&2; exit 1
fi

echo "==> creating ephemeral security group (ssh from $MYIP only)"
SG_ID=$($AWS ec2 create-security-group --group-name "karate-js-benchmark-$$" \
        --description "ephemeral: karate-js-benchmark run" --vpc-id "$VPC_ID" \
        --tag-specifications 'ResourceType=security-group,Tags=[{Key=karate-js-benchmark,Value=true}]' \
        --query GroupId --output text)
$AWS ec2 authorize-security-group-ingress --group-id "$SG_ID" \
     --protocol tcp --port 22 --cidr "$MYIP/32" > /dev/null

echo "==> launching $INSTANCE_TYPE ($AMI)"
INSTANCE_ID=$($AWS ec2 run-instances --image-id "$AMI" --instance-type "$INSTANCE_TYPE" \
   --key-name "$KEY_NAME" \
   --network-interfaces "DeviceIndex=0,SubnetId=$SUBNET_ID,Groups=$SG_ID,AssociatePublicIpAddress=true" \
   --tag-specifications 'ResourceType=instance,Tags=[{Key=karate-js-benchmark,Value=true},{Key=Name,Value=karate-js-benchmark}]' \
   --query 'Instances[0].InstanceId' --output text)
$AWS ec2 wait instance-running --instance-ids "$INSTANCE_ID"
HOST=$($AWS ec2 describe-instances --instance-ids "$INSTANCE_ID" \
       --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo "==> $INSTANCE_ID at $HOST"

SSH_OPTS=(-i "$KEY_FILE" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5)
until ssh "${SSH_OPTS[@]}" "ec2-user@$HOST" true 2> /dev/null; do sleep 3; done

echo "==> installing toolchain"
ssh "${SSH_OPTS[@]}" "ec2-user@$HOST" \
  'sudo dnf install -y -q git rsync java-21-amazon-corretto-devel maven > /dev/null'

echo "==> shipping sources (local trees — unpushed work benchmarks correctly)"
RSYNC=(rsync -az -e "ssh ${SSH_OPTS[*]}" --exclude .git --exclude 'target' --exclude '*/target' --exclude '*/*/target')
"${RSYNC[@]}" "$KARATE_SRC/" "ec2-user@$HOST:karate/"
"${RSYNC[@]}" ./ "ec2-user@$HOST:karate-js-benchmark/"

echo "==> building karate-js $SRC_VERSION and running the benchmark ${RUNS}x"
ssh "${SSH_OPTS[@]}" "ec2-user@$HOST" bash -s -- "$RUNS" "$KARATE_SHA" "$INSTANCE_TYPE" <<'REMOTE'
set -euo pipefail
RUNS=$1; KARATE_SHA=$2; TYPE=$3   # type passed from the launcher — IMDSv2 needs a token
export JAVA_HOME=$(ls -d /usr/lib/jvm/java-21-amazon-corretto* | head -1)
export PATH="$JAVA_HOME/bin:$PATH"
CPU=$(lscpu | grep -m1 -i 'model name' | cut -d: -f2 | xargs)
CORES=$(nproc)
MEM=$(awk '/MemTotal/ {printf "%.0f GB", $2/1024/1024}' /proc/meminfo)
MACHINE="$CPU, $CORES vCPU, $MEM (EC2 $TYPE, al2023, X64)"
cd karate
mvn -B -q -pl karate-js -am install -DskipTests
cd ../karate-js-benchmark
mvn -B -q clean compile
for i in $(seq 1 "$RUNS"); do
  echo "--- run $i of $RUNS ---"
  # the benchmark derives the .md path from the CSV argument, so this one
  # invocation produces both target/benchmark-$i.csv and target/benchmark-$i.md
  mvn -B -q compile exec:java \
    -Dexec.args="target/benchmark-$i.csv" \
    -Dbench.machine="$MACHINE" \
    -Dbench.karate.commit="$KARATE_SHA" \
    -Dbench.run="EC2 $TYPE on-demand, $(date -u +%F) — median of $RUNS runs by fresh-workload geomean"
done
REMOTE

echo "==> fetching artifacts"
mkdir -p target/ec2
scp -q "${SSH_OPTS[@]}" "ec2-user@$HOST:karate-js-benchmark/target/benchmark-*.csv" \
    "ec2-user@$HOST:karate-js-benchmark/target/benchmark-*.md" target/ec2/

# median selection: whole-run, by geomean of karate_vs_best over the five fresh rows
CHOSEN=$(python3 - "$RUNS" <<'PY'
import csv, math, sys
runs = int(sys.argv[1])
ROWS = {"Arithmetic", "Strings", "Objects", "Functions", "Mixed"}
scores = []
for i in range(1, runs + 1):
    with open(f"target/ec2/benchmark-{i}.csv") as f:
        vals = [float(r["karate_vs_best"]) for r in csv.DictReader(f) if r["workload"] in ROWS]
    assert len(vals) == len(ROWS), f"run {i}: expected {len(ROWS)} fresh rows, got {len(vals)}"
    scores.append((math.exp(sum(map(math.log, vals)) / len(vals)), i))
scores.sort()
for g, i in scores:
    print(f"run {i}: fresh-workload geomean {g:.4f}", file=sys.stderr)
print(scores[len(scores) // 2][1])
PY
)
echo "==> median run: $CHOSEN"
cp "target/ec2/benchmark-$CHOSEN.csv" target/ec2/benchmark-median.csv
cp "target/ec2/benchmark-$CHOSEN.md"  target/ec2/benchmark-median.md

echo
echo "==> done. To publish:"
echo "      ./etc/update-readme.py README.md target/ec2/benchmark-median.md"
echo "      cp target/ec2/benchmark-median.csv results/benchmark-$SRC_VERSION-ec2.csv"
echo "    then re-read the Analysis prose against the new table, and commit."
