import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

//NEED TO FIX VALIDATOR,CERTIFICATE,AND SLL EXCEPTIONS
/*
 * To-do list:
 * 1. Figure out how to get a server certificate using keytool
 * 2. Figure out how to use the certificate to establish connection to server in Java
 * 3. Figure out how to run a looping shell script that gets certificates for a list of websites
 * 4. Try running program with the certificate list and then update to-do list.
 * 
 * OR
 * Ignore everything above and just go with the quick and dirty solution. Figure out why it never ends.
 * Add timer to prevent infinite execution
 */
public class NewsPageGetter {
	private static final String IN_FILE_NAME = "StockWebsites.txt";
	private static final String OUT_FILE_NAME = "StockNewsPages.txt";
	private static Map<String,String> websiteMap = new HashMap<String,String> ();
	private static Map<String,String> websiteToSymMap = new HashMap<String,String> ();
	private static Map<String,String> newsPageMap = new HashMap<String,String> ();
	private static Set<String> sitesToRetry = new HashSet<String> ();
	private static int numSitesSearchedSuccessfully = 0, numSitesSearchedUnsuccessfully = 0;

	//THE FOLLOWING INITIALIZER TRUSTS ALL CERTIFICATES. THIS IS A TERRIBLE PRACTICE IN TERMS OF
	//SECURITY BUT THE ALTERNATIVE IS ACQUIRING A FILE FOR THE CERTIFICATE OF EVERY SINGLE WEBSITE
	//THAT WE INTEND TO ACCESS, WHICH IS SOMETHING WE CAN DO LATER
	static {
	    TrustManager[] trustAllCertificates = new TrustManager[] {
	        new X509TrustManager() {
	            @Override
	            public X509Certificate[] getAcceptedIssuers() {
	                return null; // Not relevant.
	            }
	            @Override
	            public void checkClientTrusted(X509Certificate[] certs, String authType) {
	                // Do nothing. Just allow them all.
	            }
	            @Override
	            public void checkServerTrusted(X509Certificate[] certs, String authType) {
	                // Do nothing. Just allow them all.
	            }
	        }
	    };

	    HostnameVerifier trustAllHostnames = new HostnameVerifier() {
	        @Override
	        public boolean verify(String hostname, SSLSession session) {
	            return true; // Just allow them all.
	        }
	    };

	    try {
	        System.setProperty("jsse.enableSNIExtension", "false");
	        SSLContext sc = SSLContext.getInstance("SSL");
	        sc.init(null, trustAllCertificates, new SecureRandom());
	        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	        HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames);
	    }
	    catch (GeneralSecurityException e) {
	        throw new ExceptionInInitializerError(e);
	    }
	}
	
	public static void main(String[] args) {
		System.setProperty("jsse.enableSNIExtension", "false");
		try {
			FileWriter writer = new FileWriter(OUT_FILE_NAME);
				
			getWebsiteMap();
			
			websiteMap.keySet().parallelStream().forEach(symbol -> {
				getNewsLinkAndWrite(writer, symbol, false);
			});
			
			System.out.println("\n\n NOW RETRYING PREVIOUSLY FAILED SITES\n");
			
			sitesToRetry.parallelStream().forEach(website -> {
				String symbol = websiteToSymMap.get(website);
				getNewsLinkAndWrite(writer, symbol, true);
				
			});
			
			numSitesSearchedSuccessfully = websiteMap.size() - numSitesSearchedUnsuccessfully;
			
			System.out.println("Now writing news pages");
			
			writeNewsPages();
			
			System.out.println("\nDone getting news sites!");
			System.out.println("Sites searched successfully: " + numSitesSearchedSuccessfully);
			System.out.println("Sites searched unsuccessfully: " + numSitesSearchedUnsuccessfully);
			
			if(writer != null)
				writer.close();
		
		}catch (FileNotFoundException e) {
			System.out.println("Input file not found!");
		}catch (IOException e) {
			System.out.println("Writing Error!");
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	private static void getNewsLinkAndWrite(FileWriter writer, String symbol, boolean retrying) {
		String website = websiteMap.get(symbol);//we dont even try http for now
		String link = ""; 
		if(website.equals("WebsiteNotFound"))
			newsPageMap.put(symbol, "NewsPageNotFound");
		else {
			link = findLink(website);
			System.out.println("News link for " + symbol + ": " + link);
			newsPageMap.put(symbol,link);
			if (link.equals("NewsPageNotFound")  && retrying) {
				numSitesSearchedUnsuccessfully += 1;
				writeNewsPage(writer,symbol);
			}
		}
		if(!retrying && !sitesToRetry.contains(website)) {
			if(link.equals("NewsPageNotFound"))
				numSitesSearchedUnsuccessfully += 1;
			writeNewsPage(writer,symbol);
		}
	}
	
	private static void writeNewsPage(FileWriter writer, String symbol) {
		try {
			writer.write(symbol + "\t\t" + websiteMap.get(symbol) + 
					"\t\t" + newsPageMap.get(symbol) + "\n");
		}catch(IOException e) {
			System.out.println("Writing error!");
		}
	}
	
	private static void getWebsiteMap() {
		try {
			Scanner fileScanner = new Scanner(new File(IN_FILE_NAME));
			while(fileScanner.hasNextLine()) {
				String company = fileScanner.nextLine();
				String[] splitStr = company.split("\t");
				String companySymbol = splitStr[0];
				String website =  "https://" + splitStr[2];//splitStr[1] is an extra \t
				//don't currently even try http
				websiteMap.put(companySymbol, website);
				websiteToSymMap.put(website, companySymbol);
			}
		}catch (FileNotFoundException e) {
			System.out.println("Input file not found!");
		}
	}
	
	public static String findLink(String website) {
		String html = fetchHTML(website);
		//System.out.println("Website html: " + html);
		String link = scrapeHTML(html);
		
		if(link == null || link.length() == 0) {
			//System.out.println("News link not found on page: " + website);
			return "NewsPageNotFound";
		}
		else if(link.charAt(0) == '/')//means that link was just an extension of the website url
			link = website + link;
		else if (!link.contains(website.substring(8))) //if the link doesnt contain the original url it is an extension
			link = website + '/' + link;
		
		return link;
	}
	
	/**
	 * This method takes a website url as input. It then navigates to the page
	 * and collects the HTML from the page in a string buffer. It finally converts that
	 * HTML to a string and returns it. If there is trouble reading the html on a website, the
	 * website is added to a set of sites to retry.
	 * 
	 * @param website url of the website whose HTML we want to fetch. NOTE that this method assumes 
	 * that the input url is complete (i.e. it contains http:// or https://)
	 */
	private static String fetchHTML(String website) {
		StringBuffer buffer = null;
		System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36"); 
		URL url = null;
		
		try {
			url = new URL(website);
		}catch(MalformedURLException e) {
			System.out.println("Problem reaching website: " + website);
			return null;
		}
		
		try {
			InputStream is = url.openStream();
			int ptr = 0;
			buffer = new StringBuffer();
			while ((ptr = is.read()) != -1) {
			    buffer.append((char)ptr);
			}
			is.close();
		} catch (SSLException sslE) {
			System.out.println("An SSL Exception occurred involving " + website);
			sitesToRetry.add(website);
		} catch (Exception ex) {
			//if(!ex.getMessage().contains("403") && !ex.getMessage().contains("Connection reset"))
				//ex.printStackTrace(); //dont want to hear about 403 or reset exceptions. we'll retry anyway
			sitesToRetry.add(website);
		}
		if(buffer == null)
			return null;
		else
			return buffer.toString();
	}
	
	/**
	 * This method scrapes the news link from the html passed to this method if it exists. If it does
	 * not, this method returns null.
	 * 
	 * @param html
	 * @return
	 */
	private static String scrapeHTML(String html) {
		String searchStr = "<a href=\"";
		String link = getLink(html,searchStr);
		
		return link;
		
	}
	
	/**
	 * Gets the link corresponding to the highest priority new-related page found on the site.
	 * If it returns null then no news-related link was found. This is the keyword prioritization
	 * system:
	 * 1. press
	 * 2. investor
	 * 3. news
	 * 4. media
	 * @param html - html on the page to be searched
	 * @param searchStr - string to be searched for on the page
	 * @return link representing the most likely link to the site's news page
	 */
	private static String getLink(String html, String searchStr) {
		//System.out.println("Now looking for links");
		if(html == null)
			return null;
		int patternLength = searchStr.length();
		int beginIndex = html.indexOf(searchStr);
		String retLink = null, link;
		int linkPriority = 5;
		
		while(beginIndex != -1) {
			link = checkedSubstr(html,beginIndex+patternLength);
			if(link == null) {
				beginIndex = html.indexOf(searchStr,beginIndex+1);
				continue;
			}
			link = link.toLowerCase(); //lower case enforces case insensitivity
			//System.out.println("Potential hit: " + link);
			if(link.contains("press") && ((linkPriority > 1) || (linkPriority == 1 && link.length() < retLink.length())) ) {
				retLink = link;
				linkPriority = 1;
			}
			else if (link.contains("investor") && ((linkPriority > 2) || (linkPriority == 2 && link.length() < retLink.length())) ) {
				linkPriority = 2;
				retLink = link;
			}
			else if (link.contains("news") && ((linkPriority > 3) || (linkPriority == 3 && link.length() < retLink.length())) ) {
				linkPriority = 3;
				retLink = link;
			}
			else if (link.contains("media") && ((linkPriority > 4) || (linkPriority == 4 && link.length() < retLink.length())) ) {
				linkPriority = 4;
				retLink = link;
			}
			//else omitted
			beginIndex = html.indexOf(searchStr,beginIndex+1);
		}
		
		if (linkPriority < 5)
			return retLink;
		else
			return "NewsPageNotFound";
	}
	
	/**
	 * This method is a modified version of substr(). It takes a string as input and
	 * appends characters until it reaches a '"' character, at which point it returns the string it
	 * has created to that point. It will also stop appending if it finds a "#" or ":"
	 * character, which represent a bad page name, at which point the method will
	 * return null.
	 * @param str
	 * @param start
	 * @return
	 */
	private static String checkedSubstr(String str, int start) {
		StringBuilder retstr = new StringBuilder();
		for(int i = start; true ; ++i) {
			char c = str.charAt(i);
			if (c == '"')
				break;
			if (c == '#')
				return null;
			retstr.append(c);
			if(retstr.length() > 100) //prevents infinite loop
				return null;
		}
		return retstr.toString();
	}
	
	private static void writeNewsPages() {
		try {
			FileWriter writer = new FileWriter(OUT_FILE_NAME);
			for(String symbol : websiteMap.keySet()) {
				writer.write(symbol + "\t\t" + websiteMap.get(symbol) + 
						"\t\t" + newsPageMap.get(symbol) + "\n");
			}
			writer.close();
		}catch (FileNotFoundException e) {
			System.out.println("Input file not found!");
		}catch (IOException e) {
			System.out.println("Writing Error!");
			e.printStackTrace();
		}
	}
}
