package thundercardbookodoosyncclient;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;



/**
 *
 * @author Olivier Sarrat (osarrat@urd.org)
 */
public class ThundercardbookOdooSyncClient {
    
    private URL serverUrl;
    private String db;
    private String username;
    private String password;
    private XmlRpcClient commonServicesClient;
    private int uid;
    private XmlRpcClient modelsServicesClient;

    public ThundercardbookOdooSyncClient() {
    }

    public ThundercardbookOdooSyncClient(URL serverUrl, String db, String username, String password) {
        this.serverUrl = serverUrl;
        this.db = db;
        this.username = username;
        this.password = password;
    }
    
    public String getServerUrlString() {
        return this.serverUrl.toExternalForm();
    }
    
    /**
     * Log in on the Odoo server
     */
    public void logIn() {
        this.commonServicesClient = new XmlRpcClient();
        
        try {
            XmlRpcClientConfigImpl commonConfig = new XmlRpcClientConfigImpl();
            commonConfig.setServerURL(
                new URL(this.serverUrl.getProtocol(),
                        this.serverUrl.getHost(),
                        this.serverUrl.getPort(), 
                        "/xmlrpc/2/common")
            );
            Object resultObject = commonServicesClient.execute(
                commonConfig, "authenticate", asList(
                    this.db, this.username, this.password, emptyMap()));
            this.uid = (int) resultObject;
            if(this.uid <= 0) {
                throw new Exception("No positive 'uid' returned with given credentials.");
            }
        } catch (Exception ex) {
            System.out.println("Unable to log in on"+this.serverUrl.toExternalForm()+" with provided credentials because:\n\t"+ex.toString());
            displayHelpAndExit(false);
        }
    }
    
    /**
     * Load Thunderbird Cardbook addon mailPopularityIndex.txt local file.
     * 
     * @return the text content of the mailPopularityIndex file
     */
    public String loadCardbookMailpopularityFile() {
        String result = "";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader("mailPopularityIndex.txt"))) {
                StringBuilder sb = new StringBuilder();                
                String line = br.readLine();
                if (line == null) {
                   throw new Exception("File mailPopularityIndex.txt has no line.");
                } else {
                    sb.append(line);
                    line = br.readLine();
                }
                while (line != null) {         
                    sb.append(System.lineSeparator());
                    sb.append(line);
                    line = br.readLine();
                }
                result = sb.toString();
            } 
        } catch (FileNotFoundException fnfex) {
            System.out.println("Unable to find local 'mailPopularityIndex.txt' file which must be in the same working directory as this program.");
            displayHelpAndExit(false); 
        } catch (Exception ex) {
            System.out.println("Unable to load local 'mailPopularityIndex.txt' file because:\n\t"+ex.toString());
            displayHelpAndExit(false);            
        }
        return result;
    }
    
    
    public String sendContactsToServer(String mailpopularityFileContent) {
        String resultUrlString = null;
        
        /*
            Create Odoo models services client
        */
        try {
            this.modelsServicesClient = new XmlRpcClient() {{
                setConfig(new XmlRpcClientConfigImpl() {{
                    setServerURL(
                        new URL(serverUrl.getProtocol(),
                                serverUrl.getHost(),
                                serverUrl.getPort(), 
                                "/xmlrpc/2/object"));
                }});
            }};
        } catch (Exception ex) {
            System.out.println("Unable to create Odoo models services client because:\n\t"+ex.toString());
            displayHelpAndExit(false);
        }
        
        /*
            Send contacts to Odoo server
        */
        try {
            resultUrlString = (String) this.modelsServicesClient.execute("execute_kw", asList(
                        this.db, this.uid, this.password,
                        "thundercardbook_sync.subscription_ignore_list", "parse_subscription_list",
                        asList(this.uid, mailpopularityFileContent.toString())
                        //asList(this.uid),
                        //new HashMap() {{ put("data_file", "Newer Partner"); }}
                            )
                        );
        } catch (Exception ex) {            
            System.out.println("Unable to send contacts to Odoo server because:\n\t"+ex.toString());
            displayHelpAndExit(false);
        }
        
        return resultUrlString;        
    }

    /**
     * Exit the program by displaying the help message.
     * @param badScriptCall if true, informs that the call to this script is incorrect
     */
    private static void displayHelpAndExit(boolean badScriptCall) {
        if (badScriptCall) {
            System.out.println("Incorrect call to this script.\n");
        }
        System.out.println("Usage: java -jar ThundercardbookOdooSynClient.jar <serverURL> <database> <username> <password>");
        System.out.println("       java -jar ThundercardbookOdooSynClient.jar -h");
        
        System.exit(0);
        
    }
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        /*
            Arguments management
        */
        if (args.length == 0) {
            displayHelpAndExit(true);
        }  else {
            if (args[0].equals("-h")) {
                displayHelpAndExit(false);                 
            } else if (args.length != 4) {
                displayHelpAndExit(true);
            }
        }
        ThundercardbookOdooSyncClient syncClient = new ThundercardbookOdooSyncClient();
        try {
            String serverUrlString = args[0];
            //Remove trailing slash if any
            if (serverUrlString.endsWith("/")) {
                serverUrlString = serverUrlString.substring(0, serverUrlString.length() - 1);
            }
            URL serverUrl = new URL(serverUrlString); 
            syncClient = new ThundercardbookOdooSyncClient(serverUrl, args[1], args[2], args[3]);
        } catch (MalformedURLException ex) {
            System.out.println("Malformed URL for <serverURL> parameter:\n\t"+ex.toString());
            displayHelpAndExit(true);
        }
        
        
        /*
            Logging in
        */
        syncClient.logIn();
        System.out.println("Logging in successful on "+syncClient.getServerUrlString()+" .");
        
        /*
            Load mailPopularityIndex.txt Thunderbird Cardbook addon local file
        */
        String mailpopularityFileContent = syncClient.loadCardbookMailpopularityFile();
        System.out.println("Successful loading of file mailPopularityIndex.txt containing "+countLines(mailpopularityFileContent)+" lines.");
        
        
        /*
            Send contacts to server
        */
        String subscriptionsFormUrl = syncClient.sendContactsToServer(mailpopularityFileContent);
        if (subscriptionsFormUrl == null || subscriptionsFormUrl.length() == 0) {
            System.out.println("Successful sending of contacts to the Odoo server, and no new contact detected.");            
        } else {
            System.out.println("Successful sending of contacts to the Odoo server, and new contacts detected.");
            
            /*
                Open the subscriptions form
            */
            subscriptionsFormUrl = subscriptionsFormUrl + "?db=" + syncClient.db;
            try {
                if(Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(syncClient.getServerUrlString() 
                                                + subscriptionsFormUrl));
                } else {
                    throw new Exception("Desktop management no supported.");
                }
            } catch (Exception ex) {
                System.out.println("Impossible to open the subscriptions form because:\n\t"+ex.toString());
            }
        }        
        
    }
    
    

    private static int countLines(String str){
       String[] lines = str.split("\r\n|\r|\n");
       return  lines.length;
    }


    
}
