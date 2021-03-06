//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel
package chemist

import funnel.instruments._

object metrics {
  val GatherAssignedLatency = lapTimer("chemist/pipeline/gather-assigned-latency")
  val MonitorCommandLatency = lapTimer("chemist/commands/monitor")
  val UnmonitorCommandLatency = lapTimer("chemist/commands/unmonitor")

  val deadFlasks   = numericGauge("chemist/flasks/dead", 0)
  val liveFlasks   = numericGauge("chemist/flasks/live", 0)
  val knownSources = numericGauge("chemist/flasks/sources", 0)
}
