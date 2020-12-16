import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class BlackboardRetriever extends Retriever{

	Networker net;
	File cookieCache;
	private static final String PATH_CACHE = "cookies.txt";
	private String cookie;

	public BlackboardRetriever() throws IOException {
		net = getNetworker();
		cookieCache = new File(PATH_CACHE);
		if(!cookieCache.exists())
			cookieCache.createNewFile();

		cookie = getCookieFromCache();
	}

	public LinkedList<Assignment> retrieve() throws InterruptedException, IOException, URISyntaxException {
		handleCookie();

		return getAssns(getClassIDs());
	}

	private void handleCookie() throws IOException {
		String cookieAttempt = this.cookie;
		if (cookieAttempt != null) {
			System.out.println("[Using Cookie]");
//			net.setCookie(cookieAttempt);
		} else {
			cookie = getCookieViaLogin();
//			net.setCookie(cookie);
		}
	}

	private LinkedList<Assignment> getAssns(LinkedList<String> classIds) throws InterruptedException {
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
		return assns;
	}

	private class ClassThread extends Thread {
		String id;
		LinkedList<Assignment> assns;
		Networker networker;

		public ClassThread(LinkedList<Assignment> assns, String id, String cookie) {
			init(assns,id);
			networker.setCookie(cookie);
		}
		
		private void init(LinkedList<Assignment> assns, String id) {
			this.id = id;
			this.assns = assns;
			this.networker = new Networker();
		}
		
		public void run() {
			try {
				String nextUrl = "https://blackboard.sc.edu/webapps/blackboard/execute/modulepage/view?course_id=" + id + "&cmp_tab_id=_564695_1&mode=view";
				networker.buildCookieRequest(nextUrl);
				String classHtml = networker.stdResponseBody();
	
				if(classHtml.contains("Assignments")){
	
					String link = "https://blackboard.sc.edu" + extractAssnsLink(classHtml, "/webapps/blackboard/content/listContent.jsp\\?course_id=" + id + "&content_id=[^&]+&mode=reset(?=[\\s\\S]{0,100}Assignments)", "Link To Assns Page", "/webapps/blackboard/content/listContent.jsp?course_id=" + id, "&mode=reset");
					assns.addAll(getAssns(networker, id, link));
	
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private class AssnsThread extends Thread {
		
		LinkedList<Assignment> assns;
		String type, subjectID, contentID, responseHTML;
		Networker networker;
		
		public AssnsThread(LinkedList<Assignment> assns, String type, String subjectID, String contentID, String responseHTML, String cookie) {
			this.assns = assns;
			this.type = type;
			this.subjectID = subjectID;
			this.contentID = contentID;
			this.responseHTML = responseHTML;
			networker = new Networker();
			networker.setCookie(cookie);
		}
		
		public void run() {
			try {
				switch (type) {
					case "Assignment": assns.add(parseAssn(networker, subjectID, contentID)); break;
					case "Test" : assns.add(parseTest(networker, subjectID, contentID)); break;
					case "Content Folder" : assns.addAll(getAssns(networker, subjectID, "https://blackboard.sc.edu" + fetchValue(responseHTML, "/webapps/blackboard/content/listContent.jsp\\?course_id=\\S{0,15}&content_id=" + contentID))); break; //todo add link
					case "McGraw-Hill Assignment Dynamic": assns.add(parseMGH(contentID, responseHTML)); break;
					case "Survey": assns.add(parseSurvery(subjectID)); break;
					default: parseOther(type);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		
	}

	private LinkedList<Assignment> getAssns(Networker networker, String subjectID, String link) throws URISyntaxException, IOException, InterruptedException {
		long time = System.currentTimeMillis();

		networker.buildCookieRequest(link);
		String responseHTML = networker.stdResponseBody();

		LinkedList<Assignment> assns = new LinkedList<>();

		//content types from clearfix liItem read
		LinkedList<String> contentTypes = extractValues(responseHTML, "clearfix liItem read", "(?<=<li[\\s\\S]{0,100}img alt=\")[^\"]+(?=\" src=\")", "Assn Page Content Types");
		//content ids from item clearfix
		LinkedList<String> contentIDs = extractValues(responseHTML, "item clearfix", "(?<=<li[\\s\\S]{0,100}img alt=\")[^\"]+(?=\" src=\")", "Content IDs");

		int idsLength = contentIDs.size(), typesLength = contentTypes.size();
		if(idsLength == 0 || typesLength == 0) {
			if(idsLength != typesLength)
				System.out.println("Uneven content and id length");
			return assns;
		}

		AssnsThread[] threads = new AssnsThread[idsLength];

		int i=0;
		for(Iterator<String> idIterator = contentIDs.iterator(), typesIterator = contentTypes.iterator(); idIterator.hasNext() && typesIterator.hasNext();i++) {
			String contentID = idIterator.next();
			String type = typesIterator.next();

			/*switch (type) {
				case "Assignment": assns.add(parseAssn(subjectID, contentID)); break;
				case "Test" : assns.add(parseTest(subjectID, contentID)); break;
				case "Content Folder" : assns.addAll(getAssns(subjectID, "https://blackboard.sc.edu" + fetchValue(responseHTML, "/webapps/blackboard/content/listContent.jsp\\?course_id=\\S{0,15}&content_id=" + contentID), net)); break; //todo add link
				case "McGraw-Hill Assignment Dynamic": assns.add(parseMGH(contentID, responseHTML)); break;
				case "Survey": assns.add(parseSurvery(subjectID)); break;
				default: parseOther(type);
			}*/
			threads[i] = new AssnsThread(assns, type, subjectID, contentID, responseHTML, networker.getCookie());
			threads[i].start();
		}

		for(AssnsThread thread : threads)
			thread.join();

		time = System.currentTimeMillis() - time;
		return assns;
	}

	private Assignment parseMGH(String contentID, String toParse)  {
//		LinkedList<String> values = fetchValues(toParse, "(?<=" + contentID + "\" ><span style=\"color:#000000;\">)[^<]+|(?<=Due Date: )[^<]+(?=[\\s\\S]{0,200}id=\"" + contentID + ")");

		String assnSubsection = extractTag(toParse, ("li id=\"contentListItem:" + contentID + "\"\tclass=\"clearfix liItem read\""), "li");

		String name = extractTag(assnSubsection, "span style=\"color:#000000;\"", "span");
		String due = extractTag(assnSubsection, "i", "i");
		due = due.substring(due.indexOf(":") + 2);

		return new Assignment(name, due);

//		return null;
	}

	private Assignment getAssn(String toParse){
		String title = extractTag(toParse, "title", "title");
		if(title.startsWith("Review Submission History:"))
			return getAssnSubmitted(toParse);
		else if(title.startsWith("Upload Assignment:"))
			return getAssnNormal(toParse);
		else {
//			System.out.println("FAIL FAIL");
			return null;
		}

	}

	private Assignment getAssnNormal(String toParse){
		//submitted assn values and shit but needs editing

		String due = extractTag(toParse, "div class=\"metaField\" aria-describedby=\"assignMeta2\"", "div");
		String name = extractTag(toParse, "span id=\"crumb_3\"", "span");

		due = due.substring(0,due.indexOf("<")).trim() + " " + extractTag(due, "span class=\"metaSubInfo\"", "span").trim();
		name = name.substring(name.indexOf(":") + 1).trim();
		return new Assignment(name, due, false);
	}

	private Assignment getAssnSubmitted(String toParse){
		String divContents = extractTag(toParse, "div class=\"attempt gradingPanelSection\"", "div");

		char[] divChars = divContents.toCharArray();
		String firstHalf = "", secondHalf = "";
		StringBuilder builder = new StringBuilder();
		int i;
		for(i=0;i<divChars.length;i++){
			builder.append(divChars[i]);
			if(divChars[i] == '\n' && divChars[i+1] == '\n')
				break;
		}
		firstHalf = builder.toString();
		builder.setLength(0);
		for(;i<divChars.length;i++){
			builder.append(divChars[i]);
		}
		secondHalf = builder.toString();

		firstHalf = extractTag(firstHalf, "p", "p");
		secondHalf = extractTag(secondHalf, "p","p");

		return new Assignment(firstHalf, secondHalf, true);
	}

	private Assignment parseAssn(Networker networker, String subjectID, String contentID) throws URISyntaxException, IOException, InterruptedException {
		long time = System.currentTimeMillis();

		networker.buildCookieRequest("https://blackboard.sc.edu/webapps/assignment/uploadAssignment?content_id=" + contentID + "&course_id=" + subjectID +"&group_id=&mode=view");

		String toParse = networker.stdResponseBody();
		Assignment assn = getAssn(toParse);
		if(assn == null) {
			System.out.println("Failed ASSN type -- reattempt");
			return parseAssn(networker, subjectID, contentID);
		}

		time = System.currentTimeMillis() - time;
		return assn;
	}

	private Assignment parseTest(Networker networker, String subjectID, String contentID) throws URISyntaxException, IOException, InterruptedException {
		long time = System.currentTimeMillis();

		networker.buildCookieRequest("https://blackboard.sc.edu/webapps/assessment/take/launchAssessment.jsp?content_id=" + contentID + "&course_id=" + subjectID +"&group_id=&mode=view");

		String htmlToParse = networker.stdResponseBody();

		//<title>Begin:
		char[] htmlChars = htmlToParse.toCharArray();
		char[] firstComparator = "Due Date".toCharArray();
		int linesToGet = 10;
		StringBuilder[] linesFound = new StringBuilder[linesToGet];
		final int[] LINES_TO_PARSE = {5,9};
		int[] findersLowerBounds = {"This Test is due on ".length(), "</li> Click <strong>Begin</strong> to start: ".length()};
		char findersUpperBoundHelper = '.';
		String[] values = new String[2];

		int firstStop = -1;
		firstloop:
		for(int i=0;i<htmlChars.length;i++)
			for(int j=0;j<firstComparator.length;j++) {
				if(htmlChars[i + j] != firstComparator[j])
					break;
				else if(j==firstComparator.length-1) {
					firstStop = i + j + 2;
					break firstloop;
				}
			}

		if(firstStop == -1){
			System.out.println("Failed TEST type -- reattempting");
			//return new Assignment("No Name (503?)", "No Due (503?)");
			return parseTest(networker,subjectID,contentID);
		}

		for(int i=0;i<linesFound.length;i++)
			linesFound[i] = new StringBuilder();

		char current = ' ';
		for(int i=firstStop, line = 0;line < linesToGet;i++) {
			current = htmlChars[i];
			linesFound[line].append(current);
			if(current == '\n')
				line++;
		}

		for(int i=0, parsed=0;i<linesFound.length;i++)
			if(i == LINES_TO_PARSE[parsed])
				values[parsed++] = linesFound[i].toString();

		for(int i=0;i<values.length;i++) {
			String trimmed = values[i].trim();
			values[i] = trimmed.substring(findersLowerBounds[i], trimmed.indexOf(findersUpperBoundHelper));
		}

		time = System.currentTimeMillis() - time;
		return new Assignment(values[1], values[0]);
	}

	private Assignment parseSurvery(String subjectID) {
		//System.out.println("Survey");

		return new Assignment("Unknown survey name", "Unknown Due");
	}

	private Assignment parseOther(String type){
		//System.out.println(type);

		return new Assignment("Unknown *unsupported name*", "Unknown Due");
	}

	/*private static String streamToString(InputStream istream){
		long time3 = System.currentTimeMillis();
		StringBuilder builder = new StringBuilder();

		try(BufferedReader reader = new BufferedReader(new InputStreamReader(istream))){
			while(reader.ready())
				builder.append((char) reader.read());
		} catch (Exception e) {
			e.printStackTrace();
		}

		time3 = System.currentTimeMillis() - time3;
		return builder.toString();
	}*/

	private LinkedList<String> getClassIDs() throws IOException {
		final String apiReqUrl = "https://blackboard.sc.edu/learn/api/v1/users/_1710804_1/memberships?expand=course.effectiveAvailability,course.permissions,courseRole&includeCount=true&limit=10000";

		String apiResponseJson = "";
		boolean cookieSuccess = true;
		do {
			apiResponseJson = getNetworkResponse(apiReqUrl, this.cookie);

			if (cookieSuccess = !(apiResponseJson.startsWith("401"))) {
				System.out.println("[Cookie Failed/Expired] --retrying w new cookie");

//				net.setCookie(null);
				this.cookie = getCookieViaLogin();
			}

		} while(!cookieSuccess);

		/*String nextUrl = "https://blackboard.sc.edu/learn/api/v1/users/_1710804_1/memberships?expand=course.effectiveAvailability,course.permissions,courseRole&includeCount=true&limit=10000";
		net.buildCookieRequest(nextUrl);
		long time = System.currentTimeMillis();
		String jsonToParse = net.stdResponseBody();
		time = System.currentTimeMillis() - time;
		if(jsonToParse.startsWith("{\"status\":401,\"message\":\"API request is not authenticated.\"}")){
			System.out.println("[Cookie Failed] --retrying login with no cookie");
			net.setCookie(null);
			getCookieViaLogin();
			return getClassIDs();
		}*/

		LinkedList<String> classLines = splitJson(apiResponseJson);
		classLines.removeIf(classLine -> !classLine.contains(getTermName()));

		LinkedList<String> classIDs = new LinkedList<>();
		for(String classLine : classLines){
			String homePageUrlLine = extractValue(classLine, "homePageUrl", "(?<=homePageUrl\":\"/webapps/blackboard/execute/courseMain\\?course_id=)[^\"]+", "Extract IDs");
			classIDs.add(homePageUrlLine.substring(homePageUrlLine.indexOf("=") + 1));
		}

		return classIDs;
		/*try{
			HttpsURLConnection test = (HttpsURLConnection) (new URL(nextUrl)).openConnection();
			test.addRequestProperty("cookie", net.getCookie());
			test.connect();
			long time2 = System.currentTimeMillis();
			String testJson = new BufferedReader(new InputStreamReader(test.getInputStream())).lines().collect(Collectors.joining("\n"));
			time2 = System.currentTimeMillis() - time2;
			System.out.println(testJson);
		} catch (Exception e){
			e.printStackTrace();
		}*/
	}

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

	private static String getNetworkResponse(String url) throws IOException {
		HttpsURLConnection connection = (HttpsURLConnection) (new URL(url)).openConnection();
		connection.setDoOutput(true);
		return streamToString(connection.getInputStream());
	}

	private static String streamToString(InputStream istream){
		return new BufferedReader(new InputStreamReader(istream)).lines().collect(Collectors.joining("\n"));
	}

	//uses cookie param to allow static access and keep thread safe. could b changed to an instance method tho
	private static String getNetworkResponse(String url, String cookie) throws IOException {
		HttpsURLConnection connection = (HttpsURLConnection) (new URL(url)).openConnection();

		connection.setRequestMethod("GET");
		connection.addRequestProperty("Cookie", cookie);
		connection.setDoOutput(true);
		connection.connect();

		if(connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED)
			return "401";
		else
			return streamToString(connection.getInputStream());

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

		System.out.println("[Without Cookie]");
		loginPageHTML = getNetworkResponse(loginUrl);

		String exec = extractValue(loginPageHTML,"execution", "(?<=name=\"execution\" value=\")[^\"]*", "Extract Exec");
		String cookie = cookiesThruPost(exec);

		saveCookieToCache(cookie);
		time = System.currentTimeMillis() - time;

		return cookie;
	}

	private String extractAssnsLink(String toParse, final String REGEX, String name, String... matchContains){
		LinkedList<String> finds = new LinkedList<>();
		char[] htmlAsArray = toParse.toCharArray();
		boolean quoteFound = false;
		String find = "";
		StringBuilder builder = new StringBuilder();

		for(char currentChar : htmlAsArray){
			if(currentChar == '"') {
				if(quoteFound){
					find = builder.toString();
					if(allContained(find, matchContains))
						finds.add(find);

					builder.setLength(0);
				}

				quoteFound = !quoteFound;
			} else if(quoteFound)
				builder.append(currentChar);
		}

		if(finds.get(1).equals("")){
			doDebug(name + " resorted to regex");
			return fetchValue(toParse, REGEX);
		}

		return finds.get(1);
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
