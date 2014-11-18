package com.delcyon.ifit;
/*
 * Created on Oct 30, 2014
 */


import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Vector;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author jeremiah
 * @version $Id: $
 */
public class IFitParser
{

    private static final int RELEVANT_HEARTRATE = 163;
	private String cookie = null;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    public IFitParser()
	{
    	simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        CookieHandler.setDefault(new CookieManager());
        IFitParser iFitParser = new IFitParser();
        try
        {
            if(args.length < 2)
            {
                System.out.println("usage: iFitParser <username> <password>");
                System.exit(1);
            }
            
            iFitParser.login(args[0],args[1]);
            
            //iFitParser.parse("https://www.ifit.com/workout/544d41f27d5b35480c847230/54515301680672d064acb168");
            
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        

    }

    private void login(String userName,String password) throws Exception
    {
        HttpClient client = new HttpClient();
        
        HttpMethod method = new GetMethod("https://www.ifit.com/login");
        method.setRequestHeader("User-Agent","Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.104 Safari/537.36");
        method.setRequestHeader("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        method.setRequestHeader("Accept-Language", "en-US,en;q=0.5");
     // Provide custom retry handler is necessary       
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,new DefaultHttpMethodRetryHandler(3, false));
        // Execute the method.
        int statusCode = client.executeMethod(method);

        if (statusCode != HttpStatus.SC_OK) 
        {
            System.err.println("Method failed: " + method.getStatusLine());
        }
        
        Header[] headers = method.getResponseHeaders();
        for (Header header : headers)
        {
            //System.out.println(header.getName()+"===>"+header.getValue());
        }
        
        cookie = (method.getResponseHeader("Set-Cookie") == null ? "" : method.getResponseHeader("Set-Cookie").getValue());
        //System.out.println("Cookie = '"+cookie+"'");
        
        method = new PostMethod("https://api.ifit.com/user/login");
        method.setRequestHeader("Cookie", cookie);
        method.setQueryString("email="+userName+"&password="+password+"&rememberMe=true");
        
        statusCode = client.executeMethod(method);

        if (statusCode != HttpStatus.SC_OK) 
        {
            System.err.println("Method failed: " + method.getStatusLine());
        }
        
        headers = method.getResponseHeaders();
        for (Header header : headers)
        {
            //System.out.println(header.getName()+"===>"+header.getValue());
        }
        
        cookie = (method.getResponseHeader("Set-Cookie") == null ? "" : method.getResponseHeader("Set-Cookie").getValue());
        //System.out.println("Cookie = '"+cookie+"'");
        paginateHistory(client,1);
    }
    
    private void paginateHistory(HttpClient client, int page) throws Exception
    {
        HttpMethod method = new GetMethod("https://www.ifit.com/me/workouts?page="+page);
        method.setRequestHeader("Cookie", cookie);
        
        int statusCode = client.executeMethod(method);

        if (statusCode != HttpStatus.SC_OK) 
        {
            System.err.println("Method failed: " + method.getStatusLine());
        }
        
//        headers = method.getResponseHeaders();
//        for (Header header : headers)
//        {
//            System.out.println(header.getName()+"===>"+header.getValue());
//        }
        
     // Read the response body.
        byte[] responseBody = method.getResponseBody();
        
        String response = new String(responseBody);
        boolean hasAdditionalPage = response.contains("?page="+(page+1)); 
       // System.out.println(response);
        
        int start = response.indexOf("workout-container");
        if(start < 0)
        {
            
            return;
        }
        response = response.substring(start);
        int stop = response.indexOf("/table");
        response = response.substring(0,stop);
        // Deal with the response.
        // Use caution: ensure correct character encoding and is not binary data
        
        //System.out.println("=======================DATA============================");
        //System.out.println(response);
        //all text between <script type="data" key="(.*)">(.*)</script>
        Vector<String> scriptStringVector = new Vector<String>();
        int fromIndex = 0;
        start = 0;
        stop = 0;
        while(start >= 0)
        {
            start = response.indexOf("<script", fromIndex);
            stop = response.indexOf("</script>", fromIndex);
            if (start < 0)
            {
                break;
            }
            
            if(response.substring(start, stop).matches("<script type=\"data\" key=\".*\">\\{.*"))
            {
                //System.out.println(response.substring(start, stop));
                scriptStringVector.add(response.substring(start, stop));
            }
            fromIndex = stop+1;
        }
        String[] dataType = new String[scriptStringVector.size()];
        JSONObject[] jsonObject = new JSONObject[scriptStringVector.size()];
        for(int index = 0; index < dataType.length; index++)
        {
            dataType[index] = scriptStringVector.get(index).replaceFirst("<script type=\"data\" key=\"(.*)\">(.*)", "$1");
            
            jsonObject[index] = new JSONObject(scriptStringVector.get(index).replaceFirst("<script type=\"data\" key=\"(.*)\">(.*)", "$2"));
            if(dataType[index].equalsIgnoreCase("log"))
            {
                //System.out.println(jsonObject[index].toString(4));
                System.out.print("\t"+(simpleDateFormat.parse(jsonObject[index].getString("start"))));
                System.out.println(" \tduration:"+(jsonObject[index].getLong("duration")/60000l)+" min. ");
                System.out.print("Cal:"+(jsonObject[index].getJSONObject("summary").getInt("totalCalories")));
                System.out.println(" Avg bpm:"+(jsonObject[index].getJSONObject("summary").optInt("averageBPM")));
                
                
               // System.out.println(jsonObject[index].getString("workoutId")+"==>"+jsonObject[index].getString("_id"));
                parse("https://www.ifit.com/workout/"+jsonObject[index].getString("workoutId")+"/"+jsonObject[index].getString("_id"));
            }
            else
            {
                System.out.println("==================================="+dataType[index].toUpperCase()+"===========================");
                System.out.print("Workout Name: \""+jsonObject[index].getString("title")+"\"");
            }
        }
        if(hasAdditionalPage)
        {
            paginateHistory(client, page+1);
        }
    }
    
    /**
     * @param string
     * @throws IOException 
     * @throws HttpException 
     */
    private void parse(String url) throws HttpException, IOException
    {
       HttpClient client = new HttpClient();
       
       HttpMethod method = new GetMethod(url);
       
       // Provide custom retry handler is necessary       
       method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,new DefaultHttpMethodRetryHandler(3, false));
    // Execute the method.
       int statusCode = client.executeMethod(method);

       if (statusCode != HttpStatus.SC_OK) {
         System.err.println("Method failed: " + method.getStatusLine());
       }

       // Read the response body.
       byte[] responseBody = method.getResponseBody();
       
       String response = new String(responseBody);
       //System.out.println(response);
       int start = response.indexOf("all-the-content");
       if(start < 0)
       {
           Header[] headers = method.getResponseHeaders();
           for (Header header : headers)
           {
               //System.out.println(header.getName()+"===>"+header.getValue());
           }
           return;
       }
       response = response.substring(start);
       int stop = response.indexOf("/div");
       response = response.substring(0,stop);
       // Deal with the response.
       // Use caution: ensure correct character encoding and is not binary data
       
       //System.out.println("=======================DATA============================");
      // System.out.println(response);
       //all text between <script type="data" key="(.*)">(.*)</script>
       Vector<String> scriptStringVector = new Vector<String>();
       int fromIndex = 0;
       start = 0;
       stop = 0;
       while(start >= 0)
       {
           start = response.indexOf("<script", fromIndex);
           stop = response.indexOf("</script>", fromIndex);
           if (start < 0)
           {
               break;
           }
           
           if(response.substring(start, stop).matches("<script type=\"data\" key=\".*\">\\{.*"))
           {
               //System.out.println(response.substring(start, stop));
               scriptStringVector.add(response.substring(start, stop));
           }
           fromIndex = stop+1;
       }
       String[] dataType = new String[scriptStringVector.size()];
       JSONObject[] jsonObject = new JSONObject[scriptStringVector.size()];
       for(int index = 0; index < dataType.length; index++)
       {
           dataType[index] = scriptStringVector.get(index).replaceFirst("<script type=\"data\" key=\"(.*)\">(.*)", "$1");
          // System.out.println("==================================="+dataType[index].toUpperCase()+"===========================");
           jsonObject[index] = new JSONObject(scriptStringVector.get(index).replaceFirst("<script type=\"data\" key=\"(.*)\">(.*)", "$2"));
           //System.out.println(jsonObject[index].toString(4));
           if(dataType[index].equalsIgnoreCase("ACTIVELOG"))
           {
               int max_bpm = jsonObject[index].getInt("max_hr");
               
               
               if(max_bpm > RELEVANT_HEARTRATE)
               {
            	   long[] max_duration = new long[max_bpm-RELEVANT_HEARTRATE];    
                   JSONArray valuesArray = jsonObject[index].getJSONObject("stats").getJSONObject("bpm").getJSONArray("values");
                   long lastTime = 0l;
                   
                   for(int valuesIndex = 0; valuesIndex < valuesArray.length(); valuesIndex++)
                   {
                       JSONArray timeBpmArray = valuesArray.getJSONArray(valuesIndex);
                       long time = timeBpmArray.getLong(0);
                       if(lastTime == 0l)
                       {
                           lastTime = time;
                       }
                       int bpm = timeBpmArray.getInt(1);
                       //working out at max
                       for(int durationIndex = 0; durationIndex < max_duration.length; durationIndex++)
                       {
                           if(bpm >= (max_bpm-durationIndex))
                           {
                               max_duration[durationIndex] += (time - lastTime);
                           }
                       }
                       lastTime = time;
                   }
                   for(int durationIndex = 0; durationIndex < max_duration.length; durationIndex++)
                   {
                	   
                	   double durationMins = max_duration[durationIndex]/60000d;
                	   double floorDurationMIns = Math.floor(durationMins);
                	   
                	   double durationRemainder = durationMins - floorDurationMIns;
                	   if(floorDurationMIns < 1d && durationRemainder < 0.5d) //skip anything less than 30 sec long as it's probably an out-lier.
                	   {
                		   continue;
                	   }
                       System.out.println("Max Heart Rate "+(durationIndex == 0 ? "== " : ">= " )+(max_bpm-durationIndex)+" bpm for "+((int)floorDurationMIns)+":"+String.format("%02d",Math.round(durationRemainder*60)) );
                   }
               }
               
               
           }
           else if (dataType[index].equalsIgnoreCase("WORKOUT"))
           {
               JSONArray controls = jsonObject[index].getJSONArray("controls");
               double maxSpeed = 0.0d;
               double duration = 0.0d;
               double lastAt = 0.0d;
               for(int controlIndex = 0; controlIndex < controls.length(); controlIndex++)
               {
                   //System.out.println("f current at = "+controls.getJSONObject(controlIndex).getDouble("at"));
                   double current = controls.getJSONObject(controlIndex).getDouble("value");
                   if(controls.getJSONObject(controlIndex).getString("type").equalsIgnoreCase("mps") == false)
                   {
                	   continue;
                   }
                   if(current > 5.37d)
                   {
                	   System.out.println(jsonObject[index].toString(4));
                   }
                   if (current > maxSpeed)
                   {
                       maxSpeed = current;
                       duration = 0.0d;
                       lastAt = controls.getJSONObject(controlIndex).getDouble("at");
                       //System.out.println("last at = "+lastAt);
                   }
                   else if (duration == 0.0d)
                   {
                       duration = controls.getJSONObject(controlIndex).getDouble("at") - lastAt;
                       //System.out.println("current at = "+controls.getJSONObject(controlIndex).getDouble("at"));
                   }
               }
               System.out.println("Max Speed = "+String.format("%.2f", (maxSpeed*2.23694d))+" mph for "+String.format("%.2f",(duration/400d))+" laps");
           }
       }
       
    }

}
