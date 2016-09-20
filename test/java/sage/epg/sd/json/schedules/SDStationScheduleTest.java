/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sage.epg.sd.json.schedules;

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;

public class SDStationScheduleTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "schedule", "retry" })
  public void deserializeRetry()
  {
    String error = "{\n" +
        "    \"response\": \"SCHEDULE_QUEUED\",\n" +
        "    \"code\": 7100,\n" +
        "    \"serverID\": \"20141201.1\",\n" +
        "    \"message\": \"The schedule you requested has been queued for generation but is not yet ready for download. Retry.\",\n" +
        "    \"datetime\": \"2014-12-04T20:38:15Z\",\n" +
        "    \"stationID\": \"50475\",\n" +
        "    \"retryTime\": \"2014-12-04T20:48:15Z\"\n" +
        "}";

    SDStationSchedule schedule = deserialize(error, SDStationSchedule.class);
    assert schedule.getCode() == 7100;
    assert schedule.retryTime() != null;
  }

  @Test(groups = {"gson", "schedulesDirect", "schedule", "error" })
  public void deserializeError()
  {
    String error = "{\n" +
        "        \"stationID\": \"10489\",\n" +
        "        \"serverID\": \"20141201.web.1\",\n" +
        "        \"code\": 2201,\n" +
        "        \"response\": \"STATIONID_DELETED\",\n" +
        "        \"programs\": [],\n" +
        "        \"metadata\": {\n" +
        "            \"code\": 2201,\n" +
        "            \"modified\": \"1970-01-01\",\n" +
        "            \"md5\": \"CAFEDEADBEEFCAFEDEADBE\",\n" +
        "            \"isDeleted\": true\n" +
        "        }\n" +
        "    }";

    SDStationSchedule schedule = deserialize(error, SDStationSchedule.class);
    assert schedule.getCode() == 2201;
  }

  @Test(groups = {"gson", "schedulesDirect", "schedule", "error" })
  public void deserializeRangeError()
  {
    String error = "{\n" +
        "        \"stationID\": \"92371\",\n" +
        "        \"serverID\": \"20141201.web.1\",\n" +
        "        \"code\": 7020,\n" +
        "        \"response\": \"SCHEDULE_RANGE_EXCEEDED\",\n" +
        "        \"minDate\": \"2015-06-21\",\n" +
        "        \"maxDate\": \"2015-07-12\",\n" +
        "        \"requestedDate\": \"2015-07-20\",\n" +
        "        \"message\": \"Date requested (2015-07-20) not within 2015-06-21 -> 2015-07-12 for stationID 92371.\"\n" +
        "    }";

    SDStationSchedule schedule = deserialize(error, SDStationSchedule.class);
    assert schedule.getCode() == 7020;
  }

  @Test(groups = {"gson", "schedulesDirect", "schedule" })
  public void deserialize()
  {
    String schedule = "{\n" +
        "    \"stationID\": \"20454\",\n" +
        "    \"programs\": [\n" +
        "      {\n" +
        "        \"programID\": \"EP003781390281\",\n" +
        "        \"airDateTime\": \"2016-08-04T00:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"UuqA6BlBsv7OjUp5Y7eIGQ\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"DD 5.1\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVPG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP007537910262\",\n" +
        "        \"airDateTime\": \"2016-08-04T01:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"sPD6NkAWxG/zQZah7BrRqQ\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"DD 5.1\",\n" +
        "          \"dvs\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TV14\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP022861070008\",\n" +
        "        \"airDateTime\": \"2016-08-04T02:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"PJcHp66iCUOc1QVzl8Qg/Q\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"DD 5.1\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TV14\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH003523780000\",\n" +
        "        \"airDateTime\": \"2016-08-04T03:00:00Z\",\n" +
        "        \"duration\": 2100,\n" +
        "        \"md5\": \"BJa532qcxi6MGGmEaKWnmQ\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP019062760190\",\n" +
        "        \"airDateTime\": \"2016-08-04T03:35:00Z\",\n" +
        "        \"duration\": 3720,\n" +
        "        \"md5\": \"Xb37xdqM7sjt1/Lc4cZF4w\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"DD 5.1\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVPG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP019952930224\",\n" +
        "        \"airDateTime\": \"2016-08-04T04:37:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"mVJakGBSNOvhMMyJcr0SVQ\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"DD 5.1\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TV14\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP008556560660\",\n" +
        "        \"airDateTime\": \"2016-08-04T05:37:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"d7dZlywT2PDjTpuzz9t8Qg\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVPG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH003523780000\",\n" +
        "        \"airDateTime\": \"2016-08-04T06:07:00Z\",\n" +
        "        \"duration\": 2100,\n" +
        "        \"md5\": \"BJa532qcxi6MGGmEaKWnmQ\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH022941750000\",\n" +
        "        \"airDateTime\": \"2016-08-04T06:42:00Z\",\n" +
        "        \"duration\": 1740,\n" +
        "        \"md5\": \"rpejzeGERLIYPL7N8YlzYA\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH015837620000\",\n" +
        "        \"airDateTime\": \"2016-08-04T07:11:00Z\",\n" +
        "        \"duration\": 1740,\n" +
        "        \"md5\": \"2k4rttEZo/PqFQTwBymDCw\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH022691780000\",\n" +
        "        \"airDateTime\": \"2016-08-04T07:40:00Z\",\n" +
        "        \"duration\": 4800,\n" +
        "        \"md5\": \"Axnv9gZfVBPKPyblY3yuTA\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH000191310000\",\n" +
        "        \"airDateTime\": \"2016-08-04T09:00:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"yR7QRioabIYYb83nkoNKVg\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH018079690000\",\n" +
        "        \"airDateTime\": \"2016-08-04T09:30:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"XfklgWtD4VShhz7dXbbKuQ\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH018079690000\",\n" +
        "        \"airDateTime\": \"2016-08-04T10:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"XfklgWtD4VShhz7dXbbKuQ\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH018079690000\",\n" +
        "        \"airDateTime\": \"2016-08-04T11:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"XfklgWtD4VShhz7dXbbKuQ\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP015057881247\",\n" +
        "        \"airDateTime\": \"2016-08-04T12:00:00Z\",\n" +
        "        \"duration\": 7200,\n" +
        "        \"md5\": \"SxCilRHfy3nHr22AjVQVug\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH011761320000\",\n" +
        "        \"airDateTime\": \"2016-08-04T14:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"ygnzI9J1hz6vbI0OC/WqDA\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVPG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH000043720000\",\n" +
        "        \"airDateTime\": \"2016-08-04T15:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"VwmLyRdOGurzp5eYR5SKiQ\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH003523740000\",\n" +
        "        \"airDateTime\": \"2016-08-04T16:00:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"irylJvv3AuJqUdxWqo0Gpw\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP000044223426\",\n" +
        "        \"airDateTime\": \"2016-08-04T16:30:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"UdZNfr/3ON4zmT34a4vEsg\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TV14\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP000042172646\",\n" +
        "        \"airDateTime\": \"2016-08-04T17:30:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"sVx5/E2qN3AkTjInQ60oEA\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"SAP\",\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TV14\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP013078551302\",\n" +
        "        \"airDateTime\": \"2016-08-04T18:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"FmeYYIEVBJ7aGNvJcfN4mg\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TV14\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP019581180401\",\n" +
        "        \"airDateTime\": \"2016-08-04T19:00:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"2FEIMQsLV+xW/IGEYoemxg\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP019581180427\",\n" +
        "        \"airDateTime\": \"2016-08-04T19:30:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"kDmcCnyNkytUfO5otk1QGw\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP005178512376\",\n" +
        "        \"airDateTime\": \"2016-08-04T20:00:00Z\",\n" +
        "        \"duration\": 3600,\n" +
        "        \"md5\": \"6x3SCNHLThWIH3Q+59Ybww\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVPG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP001887105613\",\n" +
        "        \"airDateTime\": \"2016-08-04T21:00:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"LUD59oHVRjhLqMJNB20oEQ\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVPG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP001887105584\",\n" +
        "        \"airDateTime\": \"2016-08-04T21:30:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"7GJWQ6csCEsZ1+23pPsfWg\",\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ],\n" +
        "        \"ratings\": [\n" +
        "          {\n" +
        "            \"body\": \"USA Parental Rating\",\n" +
        "            \"code\": \"TVPG\"\n" +
        "          }\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH003523770000\",\n" +
        "        \"airDateTime\": \"2016-08-04T22:00:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"lx0c4xGWHf/DZUfxepbrQQ\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP014145321201\",\n" +
        "        \"airDateTime\": \"2016-08-04T22:30:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"nr/4PoUDiupXm8YWdT4Yww\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH005371070000\",\n" +
        "        \"airDateTime\": \"2016-08-04T23:00:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"I2xO06NM+nwqwoac34B2iA\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"EP000014577691\",\n" +
        "        \"airDateTime\": \"2016-08-04T23:30:00Z\",\n" +
        "        \"duration\": 1800,\n" +
        "        \"md5\": \"WdMWPr85d9TkAAA2uvNc4Q\",\n" +
        "        \"new\": true,\n" +
        "        \"audioProperties\": [\n" +
        "          \"cc\",\n" +
        "          \"stereo\"\n" +
        "        ],\n" +
        "        \"videoProperties\": [\n" +
        "          \"hdtv\"\n" +
        "        ]\n" +
        "      }\n" +
        "    ],\n" +
        "    \"metadata\": {\n" +
        "      \"modified\": \"2016-08-04T19:03:54Z\",\n" +
        "      \"md5\": \"Q3onhNEr2SF4KieHTTLbug\",\n" +
        "      \"startDate\": \"2016-08-04\"\n" +
        "    }\n" +
        "  }";


    SDStationSchedule stationSchedule = deserialize(schedule, SDStationSchedule.class);
    assert stationSchedule.getCode() == 0;
    assert stationSchedule.getMetadata().getMd5() != null;
    assert stationSchedule.getMetadata().getModified() != null;
    assert stationSchedule.getMetadata().getStartDate() != null;

    for (SDProgramSchedule program : stationSchedule.getPrograms())
    {
      assert program.getProgramID() != null;
      assert program.getAirDateTime() != null;
      assert program.getMd5() != null;
    }
  }
}
