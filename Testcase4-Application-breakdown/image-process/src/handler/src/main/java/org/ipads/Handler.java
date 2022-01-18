/*
 * Copyright (c) 2020 Institution of Parallel and Distributed System, Shanghai Jiao Tong University
 * ServerlessBench is licensed under the Mulan PSL v1.
 * You can use this software according to the terms and conditions of the Mulan PSL v1.
 * You may obtain a copy of Mulan PSL v1 at:
 *     http://license.coscl.org.cn/MulanPSL
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR
 * PURPOSE.
 * See the Mulan PSL v1 for more details.
 */

package org.serverlessbench;

import com.cloudant.client.api.ClientBuilder;
import com.cloudant.client.api.Database;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URL;
import java.util.Date;
import java.text.SimpleDateFormat;

public class Handler {

    final static float MAX_WIDTH = 250;
    final static float MAX_HEIGHT= 250;

    public static JsonObject main(JsonObject args) throws Exception {      
        long currentTime = System.currentTimeMillis();
        
        System.out.println(" Handler invoked");

        Date date = new Date(currentTime);
        String entry_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(date.getTime());
        
        JsonArray startTimes = args.getAsJsonArray("startTimes");
        startTimes.add(entry_time);
        JsonObject response = args;

        String couchdb_url = args.get("COUCHDB_URL").getAsString();
        if(couchdb_url == null || couchdb_url.isEmpty()) {
            throw new Exception("ExtractImageMetadata: missing COUCHDB_URL: " + args.toString());
        }
        String couchdb_username = args.get("COUCHDB_USERNAME").getAsString();
        if(couchdb_username == null || couchdb_username.isEmpty()) {
            throw new Exception("ExtractImageMetadata: missing COUCHDB_USERNAME: " + args.toString());
        }
        String couchdb_password = args.get("COUCHDB_PASSWORD").getAsString();
        if(couchdb_password == null || couchdb_password.isEmpty()) {
            throw new Exception("ExtractImageMetadata: missing COUCHDB_PASSWORD: " + args.toString());
        }
        String couchdb_log_dbname = args.get("COUCHDB_LOGDB").getAsString();
        if(couchdb_log_dbname == null || couchdb_log_dbname.isEmpty()) {
            throw new Exception("ExtractImageMetadata: missing COUCHDB_LOGDB: " + args.toString());
        }

        response.add("startTimes", startTimes);
        JsonArray commTimes = args.getAsJsonArray("commTimes");
        commTimes.add(0);
        response.add("commTimes", commTimes);

        // Multiple logs are expected in retry cases
        String imageName = args.get(ImageProcessCommons.IMAGE_NAME).getAsString();
        Database db = ClientBuilder.url(new URL(couchdb_url))
                .username(couchdb_username)
                .password(couchdb_password)
                .build().database(couchdb_log_dbname, true);

        JsonObject log = new JsonObject();
        String logid = Long.toString(System.nanoTime());
        log.addProperty("_id", logid);
        log.addProperty("img", imageName);
        db.save(log);

        response.addProperty("log", logid);

        return response;
    }

    public static void main (String args[]) throws Exception {
        String jsonStr = "{\n" +
                "    \"extractedMetadata\": {\n" +
                "        \"creationTime\": \"2019:10:15 14:03:39\",\n" +
                "        \"dimensions\": {\n" +
                "            \"height\": 3968,\n" +
                "            \"width\": 2976\n" +
                "        },\n" +
                "        \"exifMake\": \"HUAWEI\",\n" +
                "        \"exifModel\": \"ALP-AL00\",\n" +
                "        \"fileSize\": \"2.372MB\",\n" +
                "        \"format\": \"image/jpeg\",\n" +
                "        \"geo\": {\n" +
                "            \"latitude\": {\n" +
                "                \"D\": 31,\n" +
                "                \"Direction\": \"N\",\n" +
                "                \"M\": 1,\n" +
                "                \"S\": 27\n" +
                "            },\n" +
                "            \"longitude\": {\n" +
                "                \"D\": 121,\n" +
                "                \"Direction\": \"E\",\n" +
                "                \"M\": 26,\n" +
                "                \"S\": 15\n" +
                "            }\n" +
                "        }\n" +
                "    },\n" +
                "    \"imageName\": \"test.jpg\"\n" +
                "}\n";
        JsonObject jsonArgs = new JsonParser().parse(jsonStr).getAsJsonObject();
        System.out.println(" Handler created jsonArgs ");
        main(jsonArgs);
    }

}
