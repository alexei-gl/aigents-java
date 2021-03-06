/*
 * MIT License
 * 
 * Copyright (c) 2005-2020 by Anton Kolonin, Aigents®
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.webstructor.cat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import net.webstructor.util.Array;
import net.webstructor.util.Str;
import net.webstructor.al.AL;
import net.webstructor.core.Environment;
import net.webstructor.main.Mainer;

public class HttpFileReader implements Reader
{
		public static final int TIMEOUT_MILLIS = 120000;//60000;//TODO:configurable
		private final static String charsetPrefix = "charset=";
		private final static String charsetMetaPrefix = "<meta http-equiv=\"content-type\" content=\"text/html; "+charsetPrefix;
    	final static String agentPrefix = "user-agent:";//was "user-agent: " but removed trailing space because of http://m.tianqi.com/robots.txt
    	final static String allowPrefix = "allow:";
    	final static String disallowPrefix = "disallow:";
    	final static String crawlDelay = "crawl-delay:";
		protected String userAgent = null; 
		//protected String content_type = null;
		//protected String content_encoding = null;
		//protected String charset = null;
		HttpFileContext defaultContext = new HttpFileContext();
		private HashMap robotsMaps = new HashMap();//map of all sites being read into respective arrays of those robots.txt files
		private HashMap crawlTimes = new HashMap();//map of all times visiting the particular site
//TODO: debug hangup on unrecogized RSS content type
		//https://stackoverflow.com/questions/595616/what-is-the-correct-mime-type-to-use-for-an-rss-feed
		private String contentTypes[] = {"text/html","text/plain","application/pdf","text/xml","application/xml","application/rss+xml"};
		protected Environment env = null;
		protected String cookies = null;//TODO: move cookies to HttpFileContext?
		private boolean debug = true;//TODO: false
		
		//http://www.utf8-chartable.de/
        private static final String urlChars[] = {"©",     "®",   	 "°"    };
        private static final String urlCodes[] = {"%C2%A9","%C2%AE"	,"%C2%B0"};
        
        private static Integer pdfLoaded = 0;// -1 - not loadable; 0 - unknown; 1 - loaded
		
        /*
        //TODO: issues with
		- java.net.ProtocolException:
		https://stackoverflow.com/questions/11022934/getting-java-net-protocolexception-server-redirected-too-many-times-error
		Reading path https://pronovostroy.ru/forum/6891-все-о-ремонте:Server redirected too many  times (20)
		java.net.ProtocolException: Server redirected too many  times (20)
		        at sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1847)
		        at sun.net.www.protocol.http.HttpURLConnection.getInputStream(HttpURLConnection.java:1440)
		        at java.net.HttpURLConnection.getResponseCode(HttpURLConnection.java:480)
		        at sun.net.www.protocol.https.HttpsURLConnectionImpl.getResponseCode(HttpsURLConnectionImpl.java:338)
		        at net.webstructor.cat.HttpFileReader.openWithRedirect(HttpFileReader.java:273)
        static {
            //https://stackoverflow.com/questions/11022934/getting-java-net-protocolexception-server-redirected-too-many-times-error
        	//CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
            //https://ru.stackoverflow.com/questions/429522/server-redirected-too-many-times-20
            HttpURLConnection.setFollowRedirects(false);//this breaks unit test for http://localtest.com/test
        }
        */
        
		public HttpFileReader(Environment env){
			this.env = env;
        }

		public HttpFileReader()
		{
        }

		public HttpFileReader(Environment env, String userAgent)
		{
			this(env);
			this.userAgent = userAgent;
        }

		//http://stackoverflow.com/questions/51438/getting-a-files-mime-type-in-java
		String getContentType(String path, URLConnection conn){
            String type = conn.getContentType();
            if (AL.empty(type))
            	type= URLConnection.guessContentTypeFromName(path);
			return type == null ? type : type.toLowerCase();
		}

	    public boolean canReadDoc(String docName){
	    	return canReadDocContext(docName,defaultContext);
	    }
	       		
		//TODO: try to get encoding from HTTP header and DON'T re-open resource twice
	    //TODO: otherwise, just reuse the connection and reget input stream without of re-opening it
	    //TODO: add conn to HttpFileContext
        public boolean canReadDocContext(String docName,HttpFileContext context)
        {
            if (docName!=null && AL.isURL(docName)) {
            	HttpURLConnection conn = null;
        		try {
        			conn = openWithRedirect(docName);
                    String typeString = getContentType(docName,conn);
                    context.content_type = Array.prefix(contentTypes, typeString);
                    if (!AL.empty(typeString) && context.content_type == null) //unsupported content type
                    	return false;
                    //https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Encoding
                    context.content_encoding = conn.getContentEncoding();
                   
                    String setcookies = getCookies(conn);
                    if (!AL.empty(setcookies))
                    	cookies = setcookies;
                    
                    if (!AL.empty(context.content_type)) {
                    	int i = typeString.indexOf(charsetPrefix, context.content_type.length());
                    	if (i != -1)
                    		context.charset = typeString.substring(i+charsetPrefix.length());
                    }
                    if (AL.empty(context.charset)) {
	                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
	        			String sCurrentLine;
	        			while ((sCurrentLine = br.readLine()) != null) {
	        				int i = sCurrentLine.toLowerCase().indexOf(charsetMetaPrefix);
	        				if (i != -1) {
	            				i += charsetMetaPrefix.length();
	        					int j = sCurrentLine.indexOf('\"', i);
	        					if (j != -1)
	        						context.charset = sCurrentLine.substring(i, j);
	        					break;
	        				}
	        			}
	        			if (br != null)
	        				br.close();
                    }
        			return true;
        		} catch (Throwable e) {
        			if (env != null)
        				env.error("HttpFileReader.canReadDocDate reading path "+docName + ": "+e.toString(), null);
        		} finally {
        			if (conn != null)
        				conn.disconnect();
        		}
            }
            return false;
        }

        //Caching robots.txt per site
        private ArrayList robotsLines(URL url) {
        	URLConnection conn = null;
        	ArrayList lines = null;
    	    try {
    			//http://docs.oracle.com/javase/tutorial/networking/urls/urlInfo.html
    			String site = new URL(url.getProtocol(), url.getHost(), url.getPort(), "/").toString();
    			lines = (ArrayList) robotsMaps.get(site);
    			if (lines != null)
    				return lines;
                robotsMaps.put(site,lines = new ArrayList());
    			url = new URL(url.getProtocol(), url.getHost(), url.getPort(), "/robots.txt");
    			//http://ru.wikipedia.org/wiki/%D0%A1%D1%82%D0%B0%D0%BD%D0%B4%D0%B0%D1%80%D1%82_%D0%B8%D1%81%D0%BA%D0%BB%D1%8E%D1%87%D0%B5%D0%BD%D0%B8%D0%B9_%D0%B4%D0%BB%D1%8F_%D1%80%D0%BE%D0%B1%D0%BE%D1%82%D0%BE%D0%B2
                conn = url.openConnection();
                conn.setConnectTimeout(TIMEOUT_MILLIS);
                if (userAgent != null)
                	conn.setRequestProperty("User-Agent", userAgent);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    			String line;
    			while ((line = br.readLine()) != null) {
    				line = line.toLowerCase().trim();
    				lines.add(line);
    			}
    			br.close();
    	    } catch (Exception e) { //no robots.txt? no problem then!  	    	
    	    } finally {
    	    	//http://stackoverflow.com/questions/9150200/closing-urlconnection-and-inputstream-correctly
    			if (conn instanceof HttpURLConnection)
    				((HttpURLConnection)conn).disconnect();
    	    }
    	    return lines;
        }

        //TODO: check all possible cases
        //http://www.robotstxt.org/robotstxt.html
        public boolean allowedForRobots(String path) {
			try {
				URL url = new URL(path);
	   		    ArrayList lines = robotsLines(url);
	        	if (lines == null)
	        		return true;
			    path = url.getPath().toLowerCase();
	        	final String thisagent = userAgent.toLowerCase();
				String line;
				String agent = null;
				boolean allowed = true; 
				Integer crawlDelaySeconds = null;
				int maskLength = 0;
	        	for (int i = 0; i < lines.size(); i++) {
					line = (String)lines.get(i);
					if (line.indexOf(agentPrefix) != -1) {
						agent = Str.parseBetween(line, agentPrefix, "#", false);
						if (!AL.empty(agent))
							agent = agent.trim();
						if (!agent.equals("*") && !thisagent.startsWith(agent))
							agent = null;
						continue;
					} 	
					if (agent == null)
						continue;
			        //https://blogs.bing.com/webmaster/2008/06/03/robots-exclusion-protocol-joining-together-to-provide-better-documentation/        
					//TODO: Visit-time
					//TODO: $
		   			if (line.indexOf(crawlDelay) != -1) {
						String str = Str.parseBetween(line, crawlDelay, "#", false);
						if (!AL.empty(str))
							crawlDelaySeconds = Integer.valueOf(str.trim());
		   			} else
	   				if (line.indexOf(disallowPrefix) != -1) {
	   					String mask = line.substring(disallowPrefix.length()).trim();
	   					if (!AL.empty(mask) & (path.startsWith(mask)
	   		   				//check if directory mask, including root directory
	   						|| (mask.endsWith("/") && ((mask = mask.substring(0,mask.length()-1)).length() == 0 || path.startsWith(mask))))){
	   						int length = mask.length();
	   						if (length < maskLength)
	   							continue;
	   						maskLength = length;
	   						allowed = false;
	   					}
	   				} else
		   			if (line.indexOf(allowPrefix) != -1) {
	   					String mask = line.substring(allowPrefix.length()).trim();
	   					String submask = Str.parseBetween(mask, "*", "*");
	   					if ((!AL.empty(submask) && path.contains(submask))
	   						|| (!AL.empty(mask) && path.startsWith(mask))) {
	   						int length = mask.length();
	   						if (length < maskLength)
	   							continue;
	   						maskLength = length;
	   						allowed = true;
	   					}
		   			}
				}
	        	if (!allowed)
	        		return false;
	        	
//TODO: consider moving it to opening url as long as url is actually opened twice!!!	        	
				if (crawlDelaySeconds != null){
					//TODO:deduplicate use this sit and the above
	    			String site = new URL(url.getProtocol(), url.getHost(), url.getPort(), "/").toString();
					//get current time
	    			long currentTime = System.currentTimeMillis();
	    			long nextTime = currentTime;
	    			long delay = crawlDelaySeconds.intValue() * 1000;
//TODO:cleanup
//if (delay==60000)//fix to debug http://www.zakupki.gov.ru
//	delay = 3000;
					//get site opening time from the map
	    			synchronized (crawlTimes) {
	    				Long lastOpened = (Long)crawlTimes.get(site);
	    				if (lastOpened != null){
							//if present, add opening time with crawlDelaySeconds
	    					nextTime = lastOpened.longValue() + delay;
	    				}
	    				crawlTimes.put(site, new Long(nextTime));
	    			}
					//if current time is less than sum, sleep for the difference of the above
	    			if (nextTime > currentTime)
						sleep(nextTime - currentTime);
				}
			} catch (MalformedURLException e) {}//no robots.txt? no problem then!
			return true;
        }
        
        public String readDocData(String docName) throws IOException
        {
        	return readDocData(docName,null);
        }
        
        HttpURLConnection open(String docName) throws IOException{
            URL url = new URL(encode(docName));
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MILLIS);
    		conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9, *;q=0.5");//TODO:
            if (cookies != null)
            	conn.setRequestProperty("Cookie", cookies);
            if (userAgent != null)
            	conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestMethod("GET");
            conn.connect();
        	return conn;
        }
        
        public static String getCookies(URLConnection conn){
        	StringBuilder sb = new StringBuilder();
            for (int i = 0;; i++) {
                String headerName = conn.getHeaderFieldKey(i);
                String headerValue = conn.getHeaderField(i);
                if (headerName == null && headerValue == null)
                	break;
                if ("Set-Cookie".equalsIgnoreCase(headerName)){
                	if (sb.length() > 0)
                		sb.append("; ");
                	sb.append(headerValue);
                }
            }
        	return sb.toString();
        }
        
        //301/302 redirect 
        //http://stackoverflow.com/questions/18701167/problems-handling-http-302-in-java-with-httpurlconnection
        HttpURLConnection openWithRedirect(String docName) throws IOException{
			HttpURLConnection conn = open(docName);
            String setcookies = getCookies(conn);//get cookies for just opened connection
            if (!AL.empty(setcookies))
            	cookies = setcookies;
            int code = conn.getResponseCode();
            int retries = 0;
            while (code == 301 || code == 302){
            	if (++retries > 5){//TODO: make configurable
            		break;
            	}
            	conn.disconnect();
            	
            	docName = conn.getHeaderField("Location");
            	//TODO: check if same domain
/**
            	//TODO:figure out why this hack fixes circular redirects for https://pronovostroy.ru/forum/6891-%D0%B2%D1%81%D0%B5-%D0%BE-%D1%80%D0%B5%D0%BC%D0%BE%D0%BD%D1%82%D0%B5/
            	int cN1 = docName.charAt(docName.length()-1);
            	int cN2 = docName.charAt(docName.length()-2);
            	if (cN1 == 47 && cN2 == 133)
            		docName = docName.substring(0,docName.length()-2)+"/";
**/            	
                if (debug && env != null)
                	env.debug("Redirecting URL code "+code+" "+docName+" set cookies:\n"+(cookies != null ? cookies : ""));
            	conn = open(docName);          	
                setcookies = getCookies(conn);
                if (!AL.empty(setcookies))
                	cookies = setcookies;
                code = conn.getResponseCode();
                if (debug && env != null)
                	env.debug("Redirecting URL code "+code+" "+docName+" got cookies:\n"+(setcookies != null ? setcookies : ""));
            }
        	return conn;
        }
        
        public String readDocData(String docName, String eol) throws IOException {
        	return readDocData(docName, eol, defaultContext); 
        }
        
        public String readDocData(String docName, String eol, HttpFileContext context) throws IOException {
        	HttpURLConnection conn = null;
            BufferedReader br = null;
    		try {
    			conn = openWithRedirect(docName);
    			//try to read pdf
    			if (!AL.empty(context.content_type) && context.content_type.endsWith("pdf")){
    				boolean pdfable = false;
    				InputStream is = null;
    				String text = "";
    				synchronized (pdfLoaded) {
    					if (pdfLoaded == 1)
    						pdfable = true;
    					else if (pdfLoaded.equals(0)) try {
        					Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
    						pdfable = true;
        					pdfLoaded = 1;
    					} catch (ClassNotFoundException e) {
        					env.error("HttpFileReader PDF missed "+docName,e);
        					pdfLoaded = -1;
    					}
    				}
    				if (pdfable) try { 
    					is = conn.getInputStream();
    				} catch (IOException e) {
    					env.error("HttpFileReader PDF no input "+docName,e);
    				}
    				if (is != null) try {
        				PDDocument document = PDDocument.load(is);
        				if (!document.isEncrypted()) try {
        				    PDFTextStripper stripper = new PDFTextStripper();
        				    text = stripper.getText(document);
//TODO: fix hack!?
        				    text = text.replaceAll("\n", " ");
        				    text = text.replaceAll("\r", " ");
        				    if (text.indexOf('\n') != -1 || text.indexOf('\r') != -1)
        				    	env.error("HttpFileReader PDF no encoding "+docName, null);
        				} catch (IOException e) {
        					env.error("HttpFileReader PDF no read "+docName,e);
        				}
        				document.close();
    				} catch( IOException e ) {
    					env.error("HttpFileReader PDF no load "+docName,e);
     				}
    				return text;
    			}
    			//if not a pdf, try to read plain text or html
            	StringBuilder sb = new StringBuilder();
    			br = context.content_encoding == null ?
        			new BufferedReader(new InputStreamReader(conn.getInputStream())) :
        			"gzip".equals(context.content_encoding) ?
        				new BufferedReader(new InputStreamReader(new GZIPInputStream(conn.getInputStream()))) :
        				new BufferedReader(new InputStreamReader(conn.getInputStream(), context.charset));
        		String sCurrentLine;
        		while ((sCurrentLine = br.readLine()) != null) {
    				sb.append(sCurrentLine);
    				if (eol != null)
    					sb.append(eol);
    			}
    			if (br != null)
    				br.close();
            	conn.disconnect();
        		return sb.toString();
    		} catch (Throwable e) {
    			String error = "HttpFileReader.readDocData reading path " + docName + ": "+e.toString()+" memory "+env.checkMemory();
    			if (env != null)
    				env.error(error, e);
    			else
    				throw new IOException(error);
    		} finally {
    			try {
    				if (br != null)br.close();
    			} catch (IOException ex) {
    				String error = "HttpFileReader.readDocData closing path " + docName + ": "+ex.toString();
    		    	if (env != null)
        				env.error(error, ex);
        			else
        				throw new IOException(error);
    			}
    			if (conn != null)
    				conn.disconnect();
    		}    		
    		return "";//TODO:null?
        }

        public static boolean isAbsoluteURL(String path) {
			try {
				URI uri = new URI(path);
				return uri.isAbsolute();
			} catch (URISyntaxException e) {}
			return false;
        }
        
        //return aligned URL, restricted to same domain of base, if strict==true
        public static String alignURL(String base, String other, boolean strict) {
			try {
				URL bURL = new URL(base);
				//TODO:fix recursive searching on 'http://aigents.com/en' - relative url formation is not right (unless with trailing slash 'http://aigents.com/en/')
				//URL bURL = null;
				//try {
				//	bURL = new URL(base).toURI().normalize().toURL();
				//} catch (URISyntaxException e) {
				//	// TODO Auto-generated catch block
				//	e.printStackTrace();
				//}
				URL rURL = new URL(bURL,other);
				String bHost = bURL.getHost();
				String rHost = rURL.getHost();
				if (!strict || bHost.equals(rHost)) {
					String url = rURL.toString();
					return url.replace("/./", "/");//TODO:find bettr way to handle this
				}
			} catch (MalformedURLException e) {}
			return null;
        }

        public static String encode(String str) {
        	return Array.replace(str, urlChars, urlCodes);
        }
        
    	protected void sleep(long millis) {
    		try {
    			Thread.sleep(millis);
    		} catch (Exception e) {
    			//TODO:log
    		}
    	}
    	
    	public static void main(String args[]){
    		java.lang.System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    		String arg = args != null && args.length > 0 ? args[0] : "https://discover.coinsquare.io/fr";
    		Mainer m = new Mainer();
    		HttpFileReader r = new HttpFileReader(m);
			try {
	    		String t = r.readDocData(arg);
	    		m.debug(t);
			} catch (IOException e) {
	    		m.error("Oops!",e);
			}
    	}
}
