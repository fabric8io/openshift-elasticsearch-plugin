#!/bin/bash
#
# Copyright (C) 2015 Red Hat, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ -n "${DEBUG:-}" ]; then
  set -x
fi
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

BIN_PATH="java"
ES_CONF="${ES_CONF:-$DIR/../../config}"

pushd "${ES_CONF}"
  echo $(pwd)
  "$BIN_PATH" $JAVA_OPTS -Dsg.display_lic_none=true -cp "$DIR/*:$DIR/../../lib/*" com.floragunn.searchguard.tools.SearchGuardAdmin "$@"
popd