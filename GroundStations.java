package GoundStations;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import gov.nasa.gsfc.spdf.ssc.client.*;
import com.google.gson.Gson;
import com.google.gdata.client.maps.*;
import com.google.gdata.data.Person;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.maps.*;
import com.google.gdata.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class GroundStations
{
    //Helper function for reading data from a given webpage
    public String readURL(URL requestURL) throws IOException
    {
        URLConnection requestConnection = requestURL.openConnection();
        InputStreamReader inStream = new InputStreamReader(requestConnection.getInputStream());
        BufferedReader buff = new BufferedReader(inStream);
        String URLstring = "";
        String nextLine = "";
        while(nextLine != null)
        {
            nextLine = buff.readLine();
            URLstring += nextLine;
        }
        
        return URLstring;
    }
    
    //Method that sends the request to Google Maps
    public String sendGMapRequest(GroundStationDescription station) throws IOException
    {
        //Build the request URL
        String request;
        String lat = Float.toString(station.getLatitude());
        String lng = Float.toString(station.getLongitude());
        request = "http://maps.googleapis.com/maps/api/geocode/json?latlng=" +
                    lat + "," +
                    lng + "&sensor=false";
        
        //Creating the URL and obtaining the content may result in exceptions
        try
        {
            //Create the URL object
            URL requestURL = new URL(request);
            return readURL(requestURL);
        }
        catch (MalformedURLException ex)
        {
            Logger.getLogger(GroundStations.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        //Function should never reach this point
        return "";
    }
    
    //Creates a new Map
    public MapEntry createMap(MapsService myService) throws Exception
    {
        URL feed = new URL("http://maps.google.com/maps/feeds/maps/default/full");
        MapFeed result = myService.getFeed(feed, MapFeed.class);
        URL mapURL = new URL(result.getEntryPostLink().getHref());
        
        MapEntry myEntry = new MapEntry();
        myEntry.setTitle(new PlainTextConstruct("Ground Stations Map"));
        myEntry.setSummary(new PlainTextConstruct("Shows locations of NASA's "
                + "satellite ground stations"));
        Person author = new Person("Dominic Guiton", null,
                 "testproject106@gmail.com");
        myEntry.getAuthors().add(author);

        return myService.insert(mapURL, myEntry);
    }
    
    //stationCode is the ISO code for the current station
    //Lat and Lng are the coordinates of the station
    public FeatureEntry createFeature(MapsService myService, String stationCode,
            String lat, String lng, MapEntry myMap)
        throws Exception
    {
        String flag = "http://icons.iconarchive.com/icons/famfamfam/flag/16/" +
                        stationCode.toLowerCase() + "-icon.png";
        //String flag = "http://127.0.0.1:80/" + stationCode + ".gif";
        URL featureEditedURL = myMap.getFeatureFeedUrl();
        FeatureEntry stationMarker = new FeatureEntry();
        
        //kml string that will be used to add a marker to myMap
        String kmlstr = "<Placemark xmlns=\"http://www.opengis.net/kml/2.2\">"
                        + "<name>" + stationCode + "</name>"
                        + "<description>Ground Station Marker</description>"
                        + "<Style>"
                        + "<IconStyle>"
                        + "<Icon>"
                        + "<href>" + flag + "</href>"
                        + "</Icon>"
                        + "</IconStyle>"
                        + "</Style>"
                        + "<Point>"
                        + "<coordinates>" + lng + "," + lat + ",0</coordinates>"
                        + "</Point>"
                        + "</Placemark>";
        
        XmlBlob KML = new XmlBlob();
        KML.setBlob(kmlstr);
        stationMarker.setKml(KML);
        
        //Set the marker's title
        stationMarker.setTitle(new PlainTextConstruct(stationCode + " Marker"));
        
        //Insert the feature and return it
        return myService.insert(featureEditedURL, stationMarker);
    }
    
    public List<String> retrieveGroundStations() throws Exception
    {
        System.setProperty("http.agent", "WsExample (" + 
                           System.getProperty("os.name") + " " + 
                           System.getProperty("os.arch") + ")");

        SatelliteSituationCenterService service =
            new SatelliteSituationCenterService(
                new URL("http://sscWeb.gsfc.nasa.gov/WS/ssc/2/SatelliteSituationCenterService?wsdl"),
                new QName("http://ssc.spdf.gsfc.nasa.gov/",
                          "SatelliteSituationCenterService"));

        SatelliteSituationCenterInterface ssc =
            service.getSatelliteSituationCenterPort();
        
        //This will be the list of stations retrieved from
        //getAllGroundStations() formatted as JSON strings
        List<String> jsonStations = new ArrayList<String>();
        
        //These stations are formated as Java objects
	List<GroundStationDescription> groundStations = ssc.getAllGroundStations();
        
        Gson gson = new Gson();
        
        //Create map
        MapsService myService = new MapsService("Ground Station Locations");
        
        //This is a dummy Google account with no information attached to it
        myService.setUserCredentials("testproject106","intershiptestproject");
        MapEntry map = createMap(myService);
        
        //Create threads to send queries to Google Maps
        ExecutorService executor = Executors.newFixedThreadPool(groundStations.size());
        
        //List of response that will arrive from Google Maps
        List<Future<String>> futurejsons;
        List<Callable<String>> googleMapRequests = new ArrayList<Callable<String>>();
        
        
        for(final GroundStationDescription station : groundStations)
        {
            //Asynchronously call sendGMapRequest() on each station in groundStations
            googleMapRequests.add(new Callable<String>()
            {
                @Override
                public String call() throws IOException
                {
                    return sendGMapRequest(station);
                }
            });
        }
            
        futurejsons = executor.invokeAll(googleMapRequests);
            
        for(int k = 0; k < groundStations.size(); k++)
        {
            //Obtain the response from google's geolocator
            String stationjson = futurejsons.get(k).get();
            
            //Find the short name of the least specific address
            //This should correspond to the ISO code of the station's country
            String targetsubstr = "short_name";
            int i = stationjson.lastIndexOf(targetsubstr);
            if(i != -1)
            {
                //The beginning of the ISO code is five characters after the end
                //of the targetsubstr
                int begin = i + targetsubstr.length() + 5;
                int end = begin + 2;
                String isoCode = stationjson.substring(begin, end);
                String lat = Float.toString(groundStations.get(k).getLatitude());
                String lng = Float.toString(groundStations.get(k).getLongitude());

                //Create a new feature to mark the location of the station
                createFeature(myService, isoCode, lat, lng, map);
            }
            
            //Use google's JSON converter function to convert each
            //GroundStationDescription object into a JSON
            jsonStations.add(gson.toJson(groundStations.get(k)));
        }
        
        //Convert each GroundStationDescription object into a JSON
        //and then append it to jsonStations
        return jsonStations;
    }
}

class Test
{
    public static void main(String[] args)
    {
        try
        {
            GroundStations stations = new GroundStations();
            List<String> stationList = stations.retrieveGroundStations();
        }
        catch (Exception ex)
        {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}