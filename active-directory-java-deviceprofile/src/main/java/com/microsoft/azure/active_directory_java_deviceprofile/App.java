package com.microsoft.azure.active_directory_java_deviceprofile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.naming.ServiceUnavailableException;

import com.microsoft.aad.adal4j.AdalErrorCode;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.DeviceCode;


/**
 * Hello world!
 *
 */
public class App 
{
	//TODO: This changes depending on Azure public cloud, Azure China, Azure Germany, or Azure government cloud
    private final static String AUTHORITY = "https://login.microsoftonline.com/";    
    private final static String CLIENT_ID = "b5efa781-c995-4398-802c-3eedc91d2a39";
    //TODO: Each resource has its own access token. Need the resource ID for each resource being accessed.
    private final static String RESOURCE = "https://graph.microsoft.com";
    

    
    //TODO: Securely persist the refresh token to be used later
    private final static Hashtable<String,AuthenticationResult> TOKENCACHE = new Hashtable<String,AuthenticationResult>();
    
    public static void main( String[] args )
    {
        //TODO: Make this a parameter
        String TENANT = "blueskyabove.onmicrosoft.com";
        
    	try
    	{
    		//Get a token using the device code
	        AuthenticationResult result = getToken(TENANT);
	        String userInfo = getUserInfoFromGraph(result.getAccessToken());
	        System.out.println(userInfo);
	        
	        //Get a token again, this time using a RefreshToken from the cache
	        result = getToken(TENANT);
	        userInfo = getUserInfoFromGraph(result.getAccessToken());
	        System.out.println(userInfo);
	        
    	}
    	catch(Exception oops)
    	{
    		System.out.println(oops.getMessage());
    	}
        
    }
    
    private static AuthenticationResult getToken(
            String tenant) throws Exception {
    	
        AuthenticationContext context;
        AuthenticationResult result = null;
        ExecutorService service = null;
       
        service = Executors.newFixedThreadPool(1);
        context = new AuthenticationContext(AUTHORITY + tenant, false, service);
        
        
        if(TOKENCACHE.containsKey(RESOURCE))
        {
        	result = TOKENCACHE.get(RESOURCE);
        	Future<AuthenticationResult> future = context.acquireTokenByRefreshToken(
                    result.getRefreshToken(), CLIENT_ID, null);
            result = future.get();
            TOKENCACHE.put(RESOURCE, result);        	
        }
        else
        {
        	Future<DeviceCode> codeFuture = context.acquireDeviceCode(CLIENT_ID, RESOURCE, null); 
        	DeviceCode codeResult = codeFuture.get();
        	System.out.println("You need to sign in.");
        	System.out.println(codeResult.getMessage());
        	
        	Boolean shouldRetry = false;
        	do {        		        
	        	try
	        	{
		        	Future<AuthenticationResult> future = context.acquireTokenByDeviceCode(codeResult, null);
		        	result = future.get();
		        	TOKENCACHE.put(RESOURCE, result);
		        	shouldRetry = false;
	        	}
	        	catch(ExecutionException oops)
	        	{	  
	        		if(oops.getCause() instanceof AuthenticationException)
	        		{
	        			AuthenticationException oops2 = (AuthenticationException)oops.getCause();
		        		System.out.println("Caught AuthenticationException: " + oops.getMessage());
		        		if(oops2.getErrorCode() == AdalErrorCode.AUTHORIZATION_PENDING)
		        		{
		        			shouldRetry = true;
		        			Long waitTime = codeResult.getInterval();
		        			System.out.println("Sleeping " + waitTime.toString());
		        			Thread.sleep(waitTime * 1000);
		        		}
		        		else
		        		{
		        			System.out.println("WTF: " + oops2.getErrorCode() + "\n" + oops2.getMessage());
		        		}
	        		}
	        	}	        		        	
        	}
        	while(shouldRetry == true);
        }

        service.shutdown();


        if (result == null) {
            throw new ServiceUnavailableException(
                    "authentication result was null");
        }
        return result;
    }
      
    private static String getUserInfoFromGraph(String accessToken) throws IOException {

        URL url = new URL("https://graph.microsoft.com/v1.0/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept","application/json");

        int httpResponseCode = conn.getResponseCode();
        if(httpResponseCode == 200) {
            BufferedReader in = null;
            StringBuilder response;
            try{
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                String inputLine;
                response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            } finally {
                in.close();
            }
            return response.toString();
        } else {
            return String.format("Connection returned HTTP code: %s with message: %s",
                    httpResponseCode, conn.getResponseMessage());
        }
    }
    
    
}
