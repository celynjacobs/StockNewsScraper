import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;

/**
 * This class can be used to generate a list of the websites corresponding to a list of stock tickers.
 * It makes several assumptions in order to work properly:
 * 1. It assumes that the input file and output file names are specified ahead of time as constants
 * 2. It assumes that the input and output files already exist
 * 3. It assumes that the first line of the input file contains irrelevant info (like labels)
 * 4. It assumes that the input file is formatted such that each line can be split on a tab to get
 * 		the stock symbol as the first element (element 0) of the split array.
 * 5. It scrapes stock data from the profile page of Yahoo! Finance for the given stock
 * 		a. If the format of those profile pages changes or the url to reach them changes, 
 * 			this algorithm won't work
 * 
 * Example of correctly-formatted input file:
 * 
 * Symbol	Description
 * AAPL		Apple, Inc.
 * PFIZ		Pfizer, Inc
 * FAMI		Farmmi, Inc.
 * ...
 * 
 * Example of output file:
 * 
 * AAPL		apple.com
 * PFIZ		pfizer.com
 * AACQU	WebsiteNotFound
 * ...
 * 
 * @author Celyn Jacobs
 *
 */
public class URLGetter {
	private static final String IN_FILE_NAME = "NASDAQ.txt";
	private static final String OUT_FILE_NAME = "StockWebsites.txt";
	private static List<String> symbols = new ArrayList<String>();
	private static Map<String,String> mainWebsites = new TreeMap<String,String> ();
	
	public static void main(String[] args) {
		getSymbols();
		getWebsites();
		writeWebsites();
	}
	
	private static void getSymbols() {
		try {
			Scanner fileScanner = new Scanner(new File(IN_FILE_NAME));
			fileScanner.nextLine(); //skip first line that just has labels
			while(fileScanner.hasNextLine()) {
				String company = fileScanner.nextLine();
				String companySymbol = company.split("\t")[0];
				symbols.add(companySymbol);
			}
		}catch (FileNotFoundException e) {
			System.out.println("Input file not found!");
		}
	}
	
	private static void getWebsites() {
		for(String sym : symbols) {
			
			Thread sideThread = new Thread ( () -> {
				WebClient client = null;
				String stockURL;
				String profileURL = "https://finance.yahoo.com/quote/" + sym + "/profile?p=" + sym;
				try {
					client = new WebClient();
					client.getOptions().setCssEnabled(false);
					client.getOptions().setJavaScriptEnabled(false);
					HtmlPage page = client.getPage(profileURL);
					if(page == null) 
						System.out.println("Page " + profileURL + " not found!");
					
					else {
						HtmlAnchor item = page.getFirstByXPath("//*[@id=\"Col1-0-Profile-Proxy\"]/section/div[1]/div/div/p[1]/a[2]");
						if(item != null) {
							stockURL = item.asText();
							System.out.println(stockURL);
						} else {
							stockURL = "WebsiteNotFound";
							System.out.println("Website for " + sym + " not found.");
						}
						addWebsite(sym, stockURL);
					}
				}catch (MalformedURLException e) {
					System.out.println("The url: " + profileURL + " was malformed!");
				}catch(Exception e){
					System.out.println("An error occurred involving " + profileURL);
					e.printStackTrace();
				}finally {
					if(client != null)
						client.close();
				}
			});
			
			sideThread.start();
			try {
				TimeUnit.MILLISECONDS.sleep(500);
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	//Solves race condition in adding entries in the map.
	private static synchronized void addWebsite(String sym, String stockURL) {
		mainWebsites.put(sym, stockURL);
	}
	
	private static void writeWebsites() {
		try {
			FileWriter writer = new FileWriter(OUT_FILE_NAME);
			for(String symbol : symbols) {
				writer.write(symbol + "\t\t" + mainWebsites.get(symbol) + "\n");
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
//Profile URL: https://finance.yahoo.com/quote/[SYMBOL]/profile?p=[SYMBOL]
//	ex: https://finance.yahoo.com/quote/AAPL/profile?p=AAPL
//Item XPath: "//*[@id="Col1-0-Profile-Proxy"]/section/div[1]/div/div/p[1]/a[2]"