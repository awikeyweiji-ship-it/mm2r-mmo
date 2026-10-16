#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/.test-output"
javac -encoding UTF-8 -d "$ROOT/.test-output" \
 "$ROOT/app/src/main/java/com/jeyu/clashgptstarter/FlowDecision.java" \
 "$ROOT/tests/FlowDecisionTest.java"
java -cp "$ROOT/.test-output" com.jeyu.clashgptstarter.FlowDecisionTest
javac -encoding UTF-8 -cp "$ROOT/.test-output" -d "$ROOT/.test-output" \
  "$ROOT/app/src/main/java/com/jeyu/clashgptstarter/UiCopy.java" \
  "$ROOT/tests/UiCopyTest.java"
java -cp "$ROOT/.test-output" UiCopyTest

javac -encoding UTF-8 -cp "$ROOT/.test-output" -d "$ROOT/.test-output" \
  "$ROOT/app/src/main/java/com/jeyu/clashgptstarter/TrustedApkPolicy.java" \
  "$ROOT/tests/TrustedApkPolicyTest.java"
java -cp "$ROOT/.test-output" TrustedApkPolicyTest

javac -encoding UTF-8 -cp "$ROOT/.test-output" -d "$ROOT/.test-output" \
  "$ROOT/app/src/main/java/com/jeyu/clashgptstarter/ChatGptSplitSelector.java" \
  "$ROOT/tests/ChatGptSplitSelectorTest.java"
java -cp "$ROOT/.test-output" com.jeyu.clashgptstarter.ChatGptSplitSelectorTest


# R5 target profiles and deterministic node ranking are pure Java (no Android stubs).
javac -encoding UTF-8 -cp "$ROOT/.test-output" -d "$ROOT/.test-output" \
 "$ROOT/app/src/main/java/com/jeyu/clashgptstarter/ServiceTarget.java" \
 "$ROOT/app/src/main/java/com/jeyu/clashgptstarter/SmartNodePolicy.java" \
 "$ROOT/tests/ServiceTargetTest.java" \
 "$ROOT/tests/SmartNodePolicyTest.java"
java -ea -cp "$ROOT/.test-output" ServiceTargetTest
java -ea -cp "$ROOT/.test-output" SmartNodePolicyTest

# R6 account-safety/official Android app routing is pure Java and testable offline.
javac -encoding UTF-8 -cp "$ROOT/.test-output" -d "$ROOT/.test-output" \
 "$ROOT/app/src/main/java/com/jeyu/clashgptstarter/ClaudeSafetyPolicy.java" \
 "$ROOT/tests/ClaudeSafetyPolicyTest.java"
java -ea -cp "$ROOT/.test-output" ClaudeSafetyPolicyTest
