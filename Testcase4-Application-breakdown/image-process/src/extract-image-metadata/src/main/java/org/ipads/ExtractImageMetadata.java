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

import java.io.File;
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
import java.util.UUID;

public class ExtractImageMetadata {
    final static long LAUNCH_TIME = System.nanoTime();

    final static float MAX_WIDTH = 250;
    final static float MAX_HEIGHT= 250;

    public static JsonObject main(JsonObject args) throws Exception {
        // Record start time and variable declaration
        long currentTime = System.currentTimeMillis();
        long dbStart = 0;
        long dbEnd = 0;
        float scalingFactor = 0;
        int height, width, scaledHeight, scaledWidth;
        String couchDBURL = null;
        String couchDBUsername = null;
        String couchDBPassword = null;
        String couchDBName = null;
        String imageName = null;
        String thumbnailName = null;
        Database db = null;
        InputStream imageStream = null;
        FileOutputStream outputStream = null;
        File f = null;
	JsonArray commTimes = new JsonArray();
        JsonObject extractedMetadata = null;
        JsonObject response = args;
        ConvertCmd cmd = new ConvertCmd();
        IMOperation op = new IMOperation();
        String thumbnailUUID = UUID.randomUUID().toString().replace("-", "");

        // Log start time and print start message
        System.out.println("ImageProcess invoked");
        response.addProperty("startTime", currentTime);

        // Validate arguments
        couchDBURL = args.get("COUCHDB_URL").getAsString();
        if (couchDBURL == null || couchDBURL.isEmpty()) {
            throw new Exception("ImageProcess: missing COUCHDB_URL: " + args.toString());
        }
        couchDBUsername = args.get("COUCHDB_USERNAME").getAsString();
        if (couchDBUsername == null || couchDBUsername.isEmpty()) {
            throw new Exception("ImageProcess: missing COUCHDB_USERNAME: " + args.toString());
        }
        couchDBPassword = args.get("COUCHDB_PASSWORD").getAsString();
        if (couchDBPassword == null || couchDBPassword.isEmpty()) {
            throw new Exception("ImageProcess: missing COUCHDB_PASSWORD: " + args.toString());
        }
        couchDBName = args.get("COUCHDB_DBNAME").getAsString();
        if (couchDBName == null || couchDBName.isEmpty()) {
            throw new Exception("ImageProcess: missing COUCHDB_DBNAME: " + args.toString());
        }
        imageName = args.get(ImageProcessCommons.IMAGE_NAME).getAsString();
        if (imageName == null || imageName.isEmpty()) {
            throw new Exception("ImageProcess: missing COUCHDB_DBNAME: " + args.toString());
        }
        response.addProperty(ImageProcessCommons.IMAGE_NAME, imageName);
        thumbnailName = "thumbnail-" + imageName;

        // Fetch image data from the database and record duration
        outputStream = new FileOutputStream(imageName);
        dbStart = System.currentTimeMillis();
        try {
            db = ClientBuilder.url(new URL(couchDBURL))
                    .username(couchDBUsername)
                    .password(couchDBPassword)
                    .build().database(couchDBName, true);
            imageStream = db.getAttachment(ImageProcessCommons.IMAGE_DOCID, imageName);
            IOUtils.copy(imageStream, outputStream);
            dbEnd = System.currentTimeMillis();
            commTimes.add(dbEnd - dbStart);
	} catch (CouchDbException e) {
            System.err.println("Image Database failure");
            e.printStackTrace();
        } finally {
            imageStream.close();
            outputStream.close();
            imageStream = null;
            outputStream = null;
            db = null;
        }

        // Extract image information and metadata
        Info imageInfo = new Info(imageName, false);
        response.addProperty(ImageProcessCommons.IMAGE_NAME, imageName);
        extractedMetadata = new Gson().toJsonTree(imageInfo).getAsJsonObject().getAsJsonObject("iAttributes");

        // Transform metadata (results saved in response)
        response = transformMetadata(extractedMetadata, response);

        // get heighth/width and calculated scaled heighth/width
        width = response.get("width").getAsInt();
        height = response.get("height").getAsInt();
        scalingFactor = Math.min(
                MAX_HEIGHT/ height,
                MAX_WIDTH / width
        );
        scaledHeight = (int) (height * scalingFactor);
        scaledWidth = (int) (width * scalingFactor);

        // Do the resizing
        op.addImage(imageName);
        op.resize(width, height);
        op.addImage(thumbnailName);
        cmd.run(op);

        // Save thumbnail to the database
        imageStream = new FileInputStream(thumbnailName);
        dbStart = System.currentTimeMillis();
        try {
            db = ClientBuilder.url(new URL(couchDBURL))
                    .username(couchDBUsername)
                    .password(couchDBPassword)
                    .build().database(couchDBName, true);
            db.saveAttachment(
	            imageStream, 
		    thumbnailUUID, 
	            response.get("format").getAsString());
            dbEnd = System.currentTimeMillis();
            commTimes.add(dbEnd - dbStart);
       } catch (CouchDbException e) {
            System.err.println("Database failure");
            e.printStackTrace();
        } finally {
            imageStream.close();
            imageStream = null;
            db = null;
        }

        // Clean up after ourselves
	f = new File(imageName);
	f.delete();
	f = new File(thumbnailName);
	f.delete();

        // Save end time and comm times to output
        response.add("commTimes", commTimes);
        currentTime = System.currentTimeMillis();
	response.addProperty("endTime", currentTime);
        return response;
    }

    public static JsonObject transformMetadata(JsonObject extractedMetadata, JsonObject response) {
        // Variable declaration
        JsonElement currentElement = null;
        JsonObject geo = new JsonObject();

        // Add creation time to result
        if (extractedMetadata.has("Properties:exif:DateTimeOriginal")) {
            response.add("creationTime", extractedMetadata.get("Properties:exif:DateTimeOriginal"));
        }

        // Add geolocation data to result
        if (extractedMetadata.has("Properties:exif:GPSLatitude") && extractedMetadata.has("Properties:exif:GPSLatitudeRef")
                && extractedMetadata.has("Properties:exif:GPSLongitude") && extractedMetadata.has("Properties:exif:GPSLongitudeRef")) {
            currentElement = parseCoordinate(
                    extractedMetadata.get("Properties:exif:GPSLatitude"),
                    extractedMetadata.get("Properties:exif:GPSLatitudeRef"));
            geo.add("latitude", currentElement);
            currentElement = parseCoordinate(
                    extractedMetadata.get("Properties:exif:GPSLongitude"),
                    extractedMetadata.get("Properties:exif:GPSLongitudeRef"));
            geo.add("longitude", currentElement);
            response.add("geo", geo);
        }

        // Parse make and model information
        if (extractedMetadata.has("Properties:exif:Make")) {
            response.add("exifMake", extractedMetadata.get("Properties:exif:Make"));
        }
        if (extractedMetadata.has("Properties:exif:Model")) {
            response.add("exifModel", extractedMetadata.get("Properties:exif:Model"));
        }

        // Add dimention information
        response.addProperty("width", Integer.valueOf(extractedMetadata.get("Width").getAsString()));
        response.addProperty("height", Integer.valueOf(extractedMetadata.get("Height").getAsString()));
        response.add("fileSize", extractedMetadata.get("Filesize"));
        response.add("format", extractedMetadata.get("Mime type"));
        return response;
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
