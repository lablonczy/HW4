import netscape.javascript.JSObject;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.text.html.parser.Entity;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class BlackboardRetriever extends Retriever{

	File cookieCache;
	private static final String PATH_CACHE_COOKIE = "cookies.txt";
	private String cookie;
	public static long avgReqTime;
	public static int reqs;

	public BlackboardRetriever() throws IOException {
		long time = System.currentTimeMillis();
		reqs = 0;
		avgReqTime = 0;

		cookieCache = new File(PATH_CACHE_COOKIE);
		if(!cookieCache.exists())
			cookieCache.createNewFile();

		time = System.currentTimeMillis() - time;
		System.out.println("----------------Constructor time: " + time);

	}

	public LinkedList<Assignment> retrieve() throws InterruptedException, IOException, URISyntaxException {
		long time = System.currentTimeMillis();

		handleCookie();
		LinkedList<Assignment> assns = getAssns(getClassDetails());
		Collections.sort(assns);

		time = System.currentTimeMillis() - time;
		System.out.println("-------------Retrieve Time: " + time);
		return assns;
	}

	protected synchronized static void updateCounter(long time){
		avgReqTime += time;
		reqs++;

		if(time >= 500)
			System.out.println("============SLOW REQUEST");
	}

	private void handleCookie() throws IOException {
		long time = System.currentTimeMillis();

		String cookieAttempt = (cookie = getCookieFromCache());
		if (cookieAttempt != null && cookieValid(cookieAttempt))
			System.out.println("[Using Cookie]");
		else
			cookie = getCookieViaLogin();

		time = System.currentTimeMillis() - time;
		System.out.println("handleCookie Time: " + time);
	}

	private LinkedList<Assignment> getAssns(HashMap<String, String> classDeets) throws InterruptedException {
		long time = System.currentTimeMillis();

		LinkedList<Assignment> assns = new LinkedList<>();
		ClassThread[] threads = new ClassThread[classDeets.size()];

		Set<Map.Entry<String, String>> detailSet = classDeets.entrySet();

		int i=0;
		for(Iterator<Map.Entry<String,String>> idsIterator = detailSet.iterator(); idsIterator.hasNext(); i++){
			Map.Entry<String,String> current = idsIterator.next();
			threads[i] = new ClassThread(assns, current.getKey(), current.getValue(), this.cookie);
			threads[i].start();
		}

		for(ClassThread thread : threads)
			thread.join();

		time = System.currentTimeMillis() - time;
		System.out.println("--------------getAssnsOuter Time: " + time);
		return assns;
	}

	/*private LinkedList<Assignment> getAssns(LinkedList<String> classIds) throws InterruptedException {
		long time = System.currentTimeMillis();

		LinkedList<Assignment> assns = new LinkedList<>();
		ClassThread[] threads = new ClassThread[classIds.size()];

		int i=0;
		for(Iterator<String> idsIterator = classIds.iterator();idsIterator.hasNext();i++){
			threads[i] = new ClassThread(assns, idsIterator.next(), this.cookie);
			threads[i].start();
		}

		for(ClassThread thread : threads)
			thread.join();

		time = System.currentTimeMillis() - time;
		System.out.println("--------------getAssnsOuter Time: " + time);
		return assns;
	}*/

	private class ClassThread extends Thread {
		String id, cookie, className;
		LinkedList<Assignment> assns;

		public ClassThread(LinkedList<Assignment> assns, String id, String className, String cookie) {
			this.id = id;
			this.assns = assns;
			this.cookie = cookie;
			this.className = className;
		}

		public void run() {
			try {

				String classAssnsApiUrl = "https://blackboard.sc.edu/learn/api/public/v2/courses/" + id + "/gradebook/columns?fields=name,grading.due";
				String apiResponseJson = getNetworkResponse(classAssnsApiUrl, this.cookie);

				LinkedList<Assignment> jsonAssns = getClassValuesFromJson(apiResponseJson);
				jsonAssns.removeIf(assn -> assn.getName().equals("Total") || assn.getName().equals("Weighted Total"));

				for(Assignment found : jsonAssns)
					found.setClassName(className);

				assns.addAll(jsonAssns);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private LinkedList<Assignment> getClassValuesFromJson(String apiResponseJson) {
		long time = System.currentTimeMillis();

		LinkedList<Assignment> assns = new LinkedList<>();
		char[] htmlAsArray = apiResponseJson.toCharArray();
		boolean quoteFound = false;
		String find = "", lastFound = "";
		StringBuilder builder = new StringBuilder();

		for(char currentChar : htmlAsArray){

			if(currentChar == '"') {
				if(quoteFound){
					find = builder.toString();
					if(lastFound.equals("name")){
						assns.add(new Assignment(find));
					} else if (lastFound.equals("due")){
						assns.getLast().setDue(find);
					}

					lastFound = find;
					builder.setLength(0);
				}

				quoteFound = !quoteFound;
			} else if(quoteFound)
				builder.append(currentChar);
		}

		time = System.currentTimeMillis() - time;
		return assns;
	}

	private HashMap<String, String> getClassDetails() throws IOException {
		long time = System.currentTimeMillis();
		final String apiReqUrl = "https://blackboard.sc.edu/learn/api/v1/users/_1710804_1/memberships?expand=course.effectiveAvailability,course.permissions,courseRole&includeCount=true&limit=10000";

		String apiResponseJson = "";
		boolean cookieFail;
		do {
			apiResponseJson = getNetworkResponse(apiReqUrl, this.cookie);

			if (cookieFail = apiResponseJson.equals("401")) {
				System.out.println("[Cookie Failed/Expired] --retrying w new cookie");
				this.cookie = getCookieViaLogin();
			}

		} while(cookieFail);

		LinkedList<String> classLines = splitJson(apiResponseJson);
		classLines.removeIf(classLine -> !classLine.contains(getTermName()));

		HashMap<String, String> classIDs = new HashMap<>();

		for(String classLine : classLines){
			String homePageUrlLine = extractValue(classLine, "homePageUrl", "(?<=homePageUrl\":\"/webapps/blackboard/execute/courseMain\\?course_id=)[^\"]+", "Extract IDs");
			classIDs.put(homePageUrlLine.substring(homePageUrlLine.indexOf("=") + 1),extractValue(classLine, "displayName", "(?<=displayName\":\")[^\"]+", "class name"));
		}

		time = System.currentTimeMillis() - time;
		System.out.println("----------------getClassIDs Time: " + time);
		return classIDs;

	}

	/*private LinkedList<String> getClassIDs() throws IOException {
		long time = System.currentTimeMillis();
		final String apiReqUrl = "https://blackboard.sc.edu/learn/api/v1/users/_1710804_1/memberships?expand=course.effectiveAvailability,course.permissions,courseRole&includeCount=true&limit=10000";

		String apiResponseJson = "";
		boolean cookieFail;
		do {
			apiResponseJson = getNetworkResponse(apiReqUrl, this.cookie);

			if (cookieFail = apiResponseJson.equals("401")) {
				System.out.println("[Cookie Failed/Expired] --retrying w new cookie");
				this.cookie = getCookieViaLogin();
			}

		} while(cookieFail);

		LinkedList<String> classLines = splitJson(apiResponseJson);
		classLines.removeIf(classLine -> !classLine.contains(getTermName()));

		LinkedList<String> classIDs = new LinkedList<>();
		for(String classLine : classLines)
			classIDs.add(extractValue(classLine, "courseId", "(?<=homePageUrl\":\"/webapps/blackboard/execute/courseMain\\?course_id=)[^\"]+", "Extract IDs"));

		time = System.currentTimeMillis() - time;
		System.out.println("----------------getClassIDs Time: " + time);
		return classIDs;

	}*/

	private LinkedList<String> splitJson(String json) {
		long time = System.currentTimeMillis();

		char[] jsonChars = json.toCharArray();
		StringBuilder builder = new StringBuilder();
		int braceCount = 0, startIndex = json.indexOf('[') + 1;
		LinkedList<String> splitJson = new LinkedList<>();

		for(int i=startIndex,length=jsonChars.length;i<length;i++){
			if(jsonChars[i] == '{')
				braceCount++;
			else if(jsonChars[i] == '}') {
				braceCount--;
				if(braceCount == 0){
					splitJson.add(builder.toString());
					builder.setLength(0);
				}
			} else if(braceCount > 0)
				builder.append(jsonChars[i]);

		}

		if(splitJson.size() == 0) {
			System.out.println("splitJson resorted to regex");
			return new LinkedList<>(Arrays.asList(json.split("\"role\":\"S\"")));
		}

		time = System.currentTimeMillis() - time;
		return splitJson;
	}

	private String getCookieFromCache() throws FileNotFoundException {
		Scanner cacheParser = new Scanner(cookieCache);
		if(cacheParser.hasNext())
			return cacheParser.nextLine();

		return null;
	}

	private boolean cookieValid(String cookie){
		String[] cookieParts = cookie.split(";");
		final long TIME_OFFSET_MS = 10000;

		for(String part : cookieParts)
			if(part.startsWith("BbRouter=expires:")){
				String expiration = part.substring(part.indexOf(":") + 1, part.indexOf(",") - 1);

				if(System.currentTimeMillis() > (Long.parseLong(expiration)*1000 + TIME_OFFSET_MS))
					return true;
			}

		return false;
	}

	private static String getNetworkResponse(String url) throws IOException {
		long time = System.currentTimeMillis();

		HttpsURLConnection connection = (HttpsURLConnection) (new URL(url)).openConnection();
		connection.setDoOutput(true);

		String response = streamToString(connection.getInputStream());

		updateCounter(System.currentTimeMillis() - time);

		return response;
	}

	private static String streamToString(InputStream istream){
		return new BufferedReader(new InputStreamReader(istream)).lines().collect(Collectors.joining("\n"));
	}

	private static String getNetworkResponse(String url, String cookie) throws IOException {
		long time = System.currentTimeMillis();

		HttpsURLConnection connection = (HttpsURLConnection) (new URL(url)).openConnection();

		connection.addRequestProperty("Cookie", cookie);
		connection.setDoOutput(true);
		connection.connect();

		if(connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
			updateCounter(System.currentTimeMillis() - time);
			System.out.println("401!!!");
			return "401";
		} else if(connection.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
			updateCounter(System.currentTimeMillis() - time);
			System.out.println("503!!!");
			return "503";
		} else {
			String response = streamToString(connection.getInputStream());

			updateCounter(System.currentTimeMillis() - time);

			return response;
		}

	}

	private static String cookiesThruPost(String exec) throws IOException {
		long time = System.currentTimeMillis();
		final String loginPostReqUrl = "https://cas.auth.sc.edu/cas/login";

		HttpsURLConnection postRequest = (HttpsURLConnection) (new URL(loginPostReqUrl)).openConnection();
		postRequest.setRequestMethod("POST");
		postRequest.addRequestProperty("Content-type", "application/x-www-form-urlencoded");
		postRequest.setDoOutput(true);

		String loginPOSTbody = "username=" + USER + "&password=" + PASS + "&execution=" + exec + "&_eventId=submit&geolocation=";

		OutputStream ostream = postRequest.getOutputStream();
		ostream.write(loginPOSTbody.getBytes(StandardCharsets.UTF_8));
		ostream.flush();
		ostream.close();

		List<String> cookies = postRequest.getHeaderFields().get("Set-Cookie");
		StringBuilder builder = new StringBuilder();
		for(String cookie : cookies)
			builder.append(cookie).append(";");

		time = System.currentTimeMillis() - time;
		updateCounter(time);
		return builder.toString();

		/*long time = System.currentTimeMillis();
		String LoginPOSTbody = "username=" + USER + "&password=" + PASS + "&execution=" + exec + "&_eventId=submit&geolocation=";
		net.setRequest(net.stdRequestBuilder(loginPostReqUrl).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(LoginPOSTbody)).build());


		LinkedList<String> cookies = new LinkedList<>(net.stdResponse().headers().allValues("set-cookie"));

		time = System.currentTimeMillis() - time;
		return cookies.get(0) + ";" + cookies.get(1) + ";" + cookies.get(2);*/

	}

	private void saveCookieToCache(String cookie) throws FileNotFoundException {
		PrintWriter cacher = new PrintWriter(cookieCache);
		cacher.println(cookie);
		cacher.close();
	}

	protected String getCookieViaLogin() throws IOException {
		long time = System.currentTimeMillis();

		String loginPageHTML;
		final String loginUrl = "https://cas.auth.sc.edu/cas/login?service=https%3A%2F%2Fblackboard.sc.edu%2Fwebapps%2Fbb-auth-provider-cas-BB5dd6acf5e22a7%2Fexecute%2FcasLogin%3Fcmd%3Dlogin%26authProviderId%3D_132_1%26redirectUrl%3Dhttps%253A%252F%252Fblackboard.sc.edu%252Fultra%26globalLogoutEnabled%3Dtrue";

		System.out.println("[Retrieving New Cookie]");
		loginPageHTML = getNetworkResponse(loginUrl);

		String exec = extractValue(loginPageHTML,"execution", "(?<=name=\"execution\" value=\")[^\"]*", "Extract Exec");
		String cookie = cookiesThruPost(exec);

		saveCookieToCache(cookie);
		time = System.currentTimeMillis() - time;

		return cookie;
	}

	private String getTermName(){
		Date date = new Date();
		LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int month = localDate.getMonthValue();
		int year = localDate.getYear();

		if(month >= 8)
			return "Fall " + year;
		else if(month <= 5)
			return "Spring " + year;
		else
			return "Summer " + year;
	}

}
