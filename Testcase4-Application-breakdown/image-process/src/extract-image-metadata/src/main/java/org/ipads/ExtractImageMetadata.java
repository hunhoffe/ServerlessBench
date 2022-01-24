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
import com.cloudant.client.org.lightcouch.CouchDbException;
import com.cloudant.client.org.lightcouch.DocumentConflictException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.core.Info;
import org.im4java.core.InfoException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Date;


public class ExtractImageMetadata {
    final static long LAUNCH_TIME = System.nanoTime();

    final static float MAX_WIDTH = 250;
    final static float MAX_HEIGHT= 250;

    public static JsonObject main(JsonObject args) throws Exception {
        JsonObject result = args;
        try {
            result = extractImageMetadata(result);
	    System.out.flush();
	    result = percolateDBInfo(args, result);
            result = transformMetadata(result);
	    System.out.flush();
            result = percolateDBInfo(args, result);
            result = handler(result);
	    System.out.flush();
            result = percolateDBInfo(args, result);
            result = thumbnail(result);
	    System.out.flush();
            result = percolateDBInfo(args, result);
            result = storeImageMetadata(result);
	    System.out.flush();
        } catch (Exception e) {
            System.out.println("main function failed");
            e.printStackTrace();
        }
        return result;
    }

    public static JsonObject percolateDBInfo(JsonObject args, JsonObject result) {
        result.add("COUCHDB_URL", args.get("COUCHDB_URL"));
        result.add("COUCHDB_USERNAME", args.get("COUCHDB_USERNAME"));
        result.add("COUCHDB_PASSWORD", args.get("COUCHDB_PASSWORD"));
        result.add("COUCHDB_DBNAME", args.get("COUCHDB_DBNAME"));
        result.add("COUCHDB_LOGDB", args.get("COUCHDB_LOGDB"));
        return result;
    }

    public static JsonObject extractImageMetadata(JsonObject args) throws Exception {
        long currentTime = System.currentTimeMillis();

        System.out.println(" ExtractImageMetadata invoked");
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
        String couchdb_dbname = args.get("COUCHDB_DBNAME").getAsString();
        if(couchdb_dbname == null || couchdb_dbname.isEmpty()) {
            throw new Exception("ExtractImageMetadata: missing COUCHDB_DBNAME: " + args.toString());
        }

        Date date = new Date(currentTime);
        String entry_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(date.getTime());
        JsonArray startTimes = new JsonArray();
        startTimes.add(entry_time);

        response.add("startTimes", startTimes);

        String imageName = args.get(ImageProcessCommons.IMAGE_NAME).getAsString(); 
        FileOutputStream outputStream = new FileOutputStream(imageName);
       
        long db_begin = System.currentTimeMillis();
	try {
            Database db = ClientBuilder.url(new URL(couchdb_url))
                    .username(couchdb_username)
                    .password(couchdb_password)
                    .build().database(couchdb_dbname, true);
            InputStream imageStream = db.getAttachment(ImageProcessCommons.IMAGE_DOCID, imageName);
            IOUtils.copy(imageStream, outputStream);
	    imageStream.close();
        } catch (CouchDbException e) {
            System.err.println("Database failure");
            e.printStackTrace();
        } finally {
            outputStream.close();
	}
	long db_finish = System.currentTimeMillis();
        long db_elapse_ms = db_finish - db_begin;
        
        JsonArray commTimes = new JsonArray();
        commTimes.add(db_elapse_ms);
        response.add("commTimes", commTimes);

        Info imageInfo = new Info(imageName, false);
        response.addProperty(ImageProcessCommons.IMAGE_NAME, imageName);
        response.add(ImageProcessCommons.EXTRACTED_METADATA, new  Gson().toJsonTree(imageInfo).getAsJsonObject().getAsJsonObject("iAttributes"));

        return response;
    }

    public static JsonObject handler(JsonObject args) throws Exception {
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

        String imageName = args.get(ImageProcessCommons.IMAGE_NAME).getAsString();
        JsonObject log = new JsonObject();
        String logid = Long.toString(System.nanoTime());
        log.addProperty("_id", logid);
        log.addProperty("img", imageName);

        long db_begin = System.currentTimeMillis();
        try {
            // Multiple logs are expected in retry cases
            Database db = ClientBuilder.url(new URL(couchdb_url))
                    .username(couchdb_username)
                    .password(couchdb_password)
                    .build().database(couchdb_log_dbname, true);
            db.save(log);
        } catch (CouchDbException e) {
            System.err.println("Database failure");
            e.printStackTrace();
        }
        long db_finish = System.currentTimeMillis();
        long db_elapse_ms = db_finish - db_begin;

        commTimes.add(db_elapse_ms);
        response.add("commTimes", commTimes);
        response.addProperty("log", logid);

        return response;
    }

    public static JsonObject storeImageMetadata(JsonObject args) throws Exception {
        long currentTime = System.currentTimeMillis();

        System.out.println(" StoreImageMetadata invoked");

        Date date = new Date(currentTime);
        String entry_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(date.getTime());
        JsonArray startTimes = args.getAsJsonArray("startTimes");
        startTimes.add(entry_time);

        JsonObject originalObj = new JsonObject();

        String couchdb_url = args.get("COUCHDB_URL").getAsString();
        if(couchdb_url == null || couchdb_url.isEmpty()) {
            throw new Exception("StoreImageMetadata: missing COUCHDB_URL " + args.toString());
        }
        String couchdb_username = args.get("COUCHDB_USERNAME").getAsString();
        if(couchdb_username == null || couchdb_username.isEmpty()) {
            throw new Exception("StoreImageMetadata: missing COUCHDB_USERNAME " + args.toString());
        }
        String couchdb_password = args.get("COUCHDB_PASSWORD").getAsString();
        if(couchdb_password == null || couchdb_password.isEmpty()) {
            throw new Exception("StoreImageMetadata: missing COUCHDB_PASSWORD " + args.toString());
        }
        String couchdb_dbname = args.get("COUCHDB_DBNAME").getAsString();
        if(couchdb_dbname == null || couchdb_dbname.isEmpty()) {
            throw new Exception("StoreImageMetadata: missing COUCHDB_DBNAME " + args.toString());
        }

        JsonObject extractedMetadata = args.getAsJsonObject(ImageProcessCommons.EXTRACTED_METADATA);
        long db_begin = System.currentTimeMillis();
        try {
            Database db = ClientBuilder.url(new URL(couchdb_url))
                    .username(couchdb_username)
                    .password(couchdb_password)
                    .build().database(couchdb_dbname, true);
            originalObj = ImageProcessCommons.findJsonObjectFromDb(db, "doc-test");
        } catch (CouchDbException e) {
            System.err.println("Database failure");
            e.printStackTrace();
        }
        long db_finish = System.currentTimeMillis();
        long db_elapse_ms = db_finish - db_begin;

        originalObj.add("startTimes", startTimes);
        JsonArray commTimes = args.getAsJsonArray("commTimes");
        commTimes.add(db_elapse_ms);
        originalObj.add("commTimes", commTimes);

        originalObj.addProperty("uploadTime", System.currentTimeMillis());
        originalObj.add("imageFormat", extractedMetadata.get("format"));
        originalObj.add("dimensions", extractedMetadata.get("dimensions"));
        originalObj.add("fileSize", extractedMetadata.get("fileSize"));
        originalObj.addProperty("userID", couchdb_username);
        originalObj.addProperty("albumID", couchdb_dbname);

        if (extractedMetadata.has("geo")) {
            originalObj.add("latitude", extractedMetadata.getAsJsonObject("geo").get("latitude"));
            originalObj.add("longtitude", extractedMetadata.getAsJsonObject("geo").get("longitude"));
        }

        if (extractedMetadata.has("exifMake")) {
            originalObj.add("exifMake", extractedMetadata.get("exifMake"));
        }

        if (extractedMetadata.has("exifModel")) {
            originalObj.add("exifModel", extractedMetadata.get("exifModel"));
        }

        if (args.has(ImageProcessCommons.THUMBNAIL)) {
            originalObj.add("thumbnail", args.get(ImageProcessCommons.THUMBNAIL));
        }
        return originalObj;
    }

    public static JsonObject thumbnail(JsonObject args) throws Exception {
        long currentTime = System.currentTimeMillis();

	System.out.println(" Thumbnail invoked");

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
        long executionTime = endTime - ExtractImageMetadata.LAUNCH_TIME;
        response.addProperty(ImageProcessCommons.EXECUTION_TIME, executionTime);

        return response;
    }

    public static JsonObject transformMetadata(JsonObject args) {
        long currentTime = System.currentTimeMillis();

        System.out.println(" TransformMetadata invoked");

        Date date = new Date(currentTime);
        String entry_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(date.getTime());
        JsonArray startTimes = args.getAsJsonArray("startTimes");
        startTimes.add(entry_time);

        JsonObject response = new JsonObject();
        JsonObject ret = new JsonObject();

        ret.add(ImageProcessCommons.IMAGE_NAME, args.get(ImageProcessCommons.IMAGE_NAME));
        ret.add("startTimes", startTimes);

        JsonArray commTimes = args.getAsJsonArray("commTimes");
        commTimes.add(0);
        ret.add("commTimes", commTimes);

        args = args.getAsJsonObject(ImageProcessCommons.EXTRACTED_METADATA);

        if (args.has("Properties:exif:DateTimeOriginal")) {
            response.add("creationTime", args.get("Properties:exif:DateTimeOriginal"));
        }
        if (args.has("Properties:exif:GPSLatitude") && args.has("Properties:exif:GPSLatitudeRef")
                && args.has("Properties:exif:GPSLongitude") && args.has("Properties:exif:GPSLongitudeRef")) {
            JsonElement latitude = parseCoordinate(args.get("Properties:exif:GPSLatitude"), args.get("Properties:exif:GPSLatitudeRef"));
            JsonElement longitude = parseCoordinate(args.get("Properties:exif:GPSLongitude"), args.get("Properties:exif:GPSLongitudeRef"));
            JsonObject geo = new JsonObject();
            geo.add("latitude", latitude);
            geo.add("longitude", longitude);
            response.add("geo", geo);
        }

        if (args.has("Properties:exif:Make")) {
            response.add("exifMake", args.get("Properties:exif:Make"));
        }
        if (args.has("Properties:exif:Model")) {
            response.add("exifModel", args.get("Properties:exif:Model"));
        }

        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("width", Integer.valueOf(args.get("Width").getAsString()));
        dimensions.addProperty("height", Integer.valueOf(args.get("Height").getAsString()));
        response.add("dimensions", dimensions);

        response.add("fileSize", args.get("Filesize"));
        response.add("format", args.get("Mime type"));

        ret.add(ImageProcessCommons.EXTRACTED_METADATA, response);
        return ret;
    }

    static JsonElement parseCoordinate(JsonElement coordinate, JsonElement coordinateDircetion)  {
        String[] degreeArray = coordinate.getAsString().split(",")[0].trim().split("/");
        String[] minuteArray = coordinate.getAsString().split(",")[1].trim().split("/");
        String[] secondArray = coordinate.getAsString().split(",")[2].trim().split("/");

        JsonObject ret = new JsonObject();
        ret.addProperty("D", (Integer.valueOf(degreeArray[0])) / (Integer.valueOf(degreeArray[1])));
        ret.addProperty("M", (Integer.valueOf(minuteArray[0])) / (Integer.valueOf(minuteArray[1])));
        ret.addProperty("S", (Integer.valueOf(secondArray[0])) / (Integer.valueOf(secondArray[1])));
        ret.add("Direction", coordinateDircetion);
        return ret;
    }
}
