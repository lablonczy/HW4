import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Networker {

	private HttpClient client;
	private HttpRequest request;
	private HttpResponse<String> response;
	private String cookie;

	private static final HttpResponse.BodyHandler<String> BASIC_HANDLER = HttpResponse.BodyHandlers.ofString();
	
	public static long avgReqTime;
	public static int reqs;
	
	public Networker() {
		client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		cookie = "";
		request = null;
		response = null;
		reqs = 0;
		avgReqTime = 0;
	}
	
	protected void setRequest(HttpRequest request) {
		this.request = request;
	}
	
	protected void setCookie(String cookie) {
		this.cookie = cookie;
	}
	
	protected String getCookie() {
		return this.cookie;
	}
	
	protected void buildCookieRequest(String url) throws URISyntaxException {
		request = stdRequestBuilder(url).header("Cookie", cookie).build();
	}

	protected HttpRequest.Builder stdRequestBuilder(String url) throws URISyntaxException {
		return HttpRequest.newBuilder(new URI(url));
	}

	protected HttpRequest buildStdRequest(String url) throws URISyntaxException {
		request = stdRequestBuilder(url).build();
		return request;
	}

	protected String stdResponseBody() throws IOException, InterruptedException {
		return stdResponse().body();
	}

	protected HttpResponse<String> stdResponse() throws IOException, InterruptedException {
		long time = System.currentTimeMillis();
		response = client.send(request, BASIC_HANDLER);
		updateCounter(System.currentTimeMillis() - time);
		
		return response;
	}

	protected synchronized static void updateCounter(long time){
		avgReqTime += time;
		reqs++;

		if(time >= 700)
			System.out.println("============SLOW REQUEST");
	}
	
}
