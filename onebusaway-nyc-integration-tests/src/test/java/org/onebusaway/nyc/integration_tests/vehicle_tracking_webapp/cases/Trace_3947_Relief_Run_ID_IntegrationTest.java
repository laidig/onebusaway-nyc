
/**
 * Copyright (c) 2011 Metropolitan Transportation Authority
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.nyc.integration_tests.vehicle_tracking_webapp.cases;

import org.onebusaway.nyc.integration_tests.vehicle_tracking_webapp.AbstractTraceRunner;


public class Trace_3947_Relief_Run_ID_IntegrationTest extends AbstractTraceRunner {

  public Trace_3947_Relief_Run_ID_IntegrationTest() throws Exception {
    super("3947_Relief_Run_ID.csv");
    setBundle("2016Jan_ManBC_WkdOnly", "2016-01-27T10:01:00EDT");
  }
}
