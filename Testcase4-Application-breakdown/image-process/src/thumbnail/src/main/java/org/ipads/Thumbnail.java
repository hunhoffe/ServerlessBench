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

import com.cloudant.client.org.lightcouch.CouchDbException;
import com.cloudant.client.api.ClientBuilder;
import com.cloudant.client.api.Database;
import com.cloudant.client.org.lightcouch.DocumentConflictException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.text.SimpleDateFormat;

public class Thumbnail {

    final static long LAUNCH_TIME = System.nanoTime();

    final static float MAX_WIDTH = 250;
    final static float MAX_HEIGHT= 250;

    public static JsonObject main(JsonObject args) throws Exception {
        long currentTime = System.currentTimeMillis();
        Date date = new Date(currentTime);
        String entry_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(date.getTime());
        JsonArray startTimes = args.getAsJsonArray("startTimes");
        startTimes.add(entry_time);

        JsonObject response = args;

        String couchdb_url = args.get("COUCHDB_URL").getAsString();
        if(couchdb_url == null || couchdb_url.isEmpty()) {
            throw new Exception("Thumbnail: missing COUCHDB_URL " + args.toString());
        }
        String couchdb_username = args.get("COUCHDB_USERNAME").getAsString();
        if(couchdb_username == null || couchdb_username.isEmpty()) {
            throw new Exception("Thumbnail: missing COUCHDB_USERNAME " + args.toString());
        }
        String couchdb_password = args.get("COUCHDB_PASSWORD").getAsString();
        if(couchdb_password == null || couchdb_password.isEmpty()) {
            throw new Exception("Thumbnail: missing COUCHDB_PASSWORD " + args.toString());
        }
        String couchdb_dbname = args.get("COUCHDB_DBNAME").getAsString();
        if(couchdb_dbname == null || couchdb_dbname.isEmpty()) {
            throw new Exception("Thumbnail: missing COUCHDB_DBNAME " + args.toString());
        }

        response.add("startTimes", startTimes);

        String imageName = args.get(ImageProcessCommons.IMAGE_NAME).getAsString();
        FileOutputStream outputStream = new FileOutputStream(imageName);

        long db_begin = System.currentTimeMillis();
        try {
	    Database db = ClientBuilder.url(new URL(couchdb_url))
                    .username(couchdb_username)
                    .password(couchdb_password)
                    .build().database(couchdb_dbname, true);
            InputStream imageStream = db.getAttachment("doc-test", imageName);
            IOUtils.copy(imageStream, outputStream);
	    imageStream.close();
	} catch (CouchDbException e) {
            System.err.println("Database failure");
            e.printStackTrace();
        }
	long db_finish = System.currentTimeMillis();
        long db_elapse_ms = db_finish - db_begin;

	outputStream.close();

	JsonObject size = args.getAsJsonObject(ImageProcessCommons.EXTRACTED_METADATA)
                .getAsJsonObject("dimensions");
        int width = size.get("width").getAsInt();
        int height = size.get("height").getAsInt();

        float scalingFactor = Math.min(MAX_HEIGHT/ height, MAX_WIDTH / width);
        width = (int) (width * scalingFactor);
        height = (int) (height * scalingFactor);

        String thumbnailName = "thumbnail-" + imageName;
        ConvertCmd cmd = new ConvertCmd();
        IMOperation op = new IMOperation();
        op.addImage(imageName);
        op.resize(width, height);
        op.addImage(thumbnailName);
        cmd.run(op);

        InputStream imageStream = new FileInputStream(thumbnailName);
	db_begin = System.currentTimeMillis();
        try {
	    Database db = ClientBuilder.url(new URL(couchdb_url))
                    .username(couchdb_username)
                    .password(couchdb_password)
                    .build().database(couchdb_dbname, true);
	    JsonObject doc = ImageProcessCommons.findJsonObjectFromDb(db, "doc-test");
	    db.saveAttachment(imageStream, thumbnailName,
            	    args.get(ImageProcessCommons.EXTRACTED_METADATA).getAsJsonObject().get("format").getAsString(),
                    doc.get("_id").getAsString(),
                    doc.get("_rev").getAsString());
	} catch (DocumentConflictException e) {
            System.err.println("Document Conflict Exception when writing thumbnail; ignoring...");
	} catch (CouchDbException e) {
            System.err.println("Database failure");
            e.printStackTrace();
        }
	db_finish = System.currentTimeMillis();
        db_elapse_ms += db_finish - db_begin;

        imageStream.close();

        response.addProperty(ImageProcessCommons.THUMBNAIL, thumbnailName);
   
        JsonArray commTimes = args.getAsJsonArray("commTimes");
        commTimes.add(db_elapse_ms);
        response.add("commTimes", commTimes);

        long endTime = System.nanoTime();
        long executionTime = endTime - Thumbnail.LAUNCH_TIME;
        response.addProperty(ImageProcessCommons.EXECUTION_TIME, executionTime);

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
        main(jsonArgs);
    }

}
