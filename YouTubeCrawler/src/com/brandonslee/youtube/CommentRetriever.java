package com.brandonslee.youtube;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.opencsv.CSVWriter;

/*
 * 대상 video: https://www.youtube.com/watch?v=cyohHyQl-kc
 * REST API(JSON result): https://www.googleapis.com/youtube/v3/commentThreads?part=snippet,replies&videoId=cyohHyQl-kc&key=mykey
 * next page: https://www.googleapis.com/youtube/v3/commentThreads?part=snippet,replies&videoId=cyohHyQl-kc&pageToken=Cg0Q647blI7zzwIgACgBEhQIABCwq_C58vLPAhigyOq2sO3PAhgCIBQog6Xv69KE4tSeAQ==&key=mykey
 */

public class CommentRetriever {
	private static String encodingString = "utf-8";

	protected static String restAPI = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet,replies";

	private String apiKey = null;
	private int maxResults = 20;	// 1 ~ 100. default is 20

	private String videoId = null;
	private int pageNum = 1;
	private String textFormat = null;	// plainText or HTML(default)
	private String order = null;	// time(default) or relevance
	private String nextPageToken = null;

	private StringBuffer jsonStrBuf = null;
	private List<String[]> commentList;

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public int getMaxResults() {
		return maxResults;
	}

	public void setMaxResults(int maxResults) throws IOException {
		if (maxResults < 1 || maxResults > 100) throw new IOException("Wrong maxResults value in api.txt. It should be 1 ~ 100.");
		this.maxResults = maxResults;
	}

	public String getVideoId() {
		return videoId;
	}

	public void setVideoId(String videoId) {
		this.videoId = videoId;
	}

	public int getPageNum() {
		return pageNum;
	}

	public void setPageNum(int pageNum) {
		if (pageNum < 1)
			this.pageNum = 1;
		else
			this.pageNum = pageNum;
	}

	public String getTextFormat() {
		return textFormat;
	}

	public void setTextFormat(String textFormat) {
		if (textFormat.equals("h"))
			this.textFormat = "HTML";
		else
			this.textFormat = "plainText";
	}

	public String getOrder() {
		return order;
	}

	public void setOrder(String order) {
		if (order.equals("r"))	// default는 "time"
			this.order = "relevance";
		else
			this.order = "time";
	}

	public String getNextPageToken() {
		return nextPageToken;
	}

	public void setNextPageToken(String nextPageToken) {
		this.nextPageToken = nextPageToken;
	}

	// line 1: api key
	// line 2: max result
	public void getKeyAndConditions() throws IOException {
		String fileName = "api.txt";
		FileReader fileReader = new FileReader(fileName);
		BufferedReader br = new BufferedReader(fileReader);
		String str = br.readLine();
		if (str != null)
			setApiKey(str);
		str = br.readLine();
		if (str != null)
			this.setMaxResults(Integer.parseInt(str));

		br.close();
		fileReader.close();
	}

	public void initCommentList() {
		commentList = new ArrayList<String[]>();	// comment를 List에 담기

		// header
		// replies의 경우 id에 parentId 값을 가지고 있으므로, parentId는 굳이 필요 없음.
		//commentList.add(new String[] {"Author", "Date(updatedAt)", "Like", "Comment", "Id"});
		commentList.add(new String[] {"Author", "ReplyTo", "Date(updatedAt)", "Like", "Comment"});
	}

	public void getComments()
		throws MalformedURLException, ProtocolException, IOException, RuntimeException {
		String urlStr = restAPI + "&videoId=" + this.getVideoId() + "&order=" + this.getOrder()
				+ "&maxResults=" + this.getMaxResults()
				+ "&textFormat=" + this.getTextFormat()
				+ "&key=" + apiKey;
		if (this.getNextPageToken() != null)
			urlStr += "&pageToken=" + this.getNextPageToken();
		System.out.println(urlStr);
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");

		if (conn.getResponseCode() != 200) {
			throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
		}

		// utf-8 으로 읽어야만 사용중인 시스템의 인코딩에 상관없이 다국어 안 깨짐
		BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), CommentRetriever.encodingString));

		String output = null;
		jsonStrBuf = new StringBuffer();
		while ((output = br.readLine()) != null) {
			//System.out.println(output);
			jsonStrBuf.append(output);
		}
		conn.disconnect();
	}

	private String[] getacomment(String replyTo, JSONObject commentObject) {
    	String[] commentData = new String[5];
		commentData[0] = commentObject.get("authorDisplayName").toString();
		commentData[1] = replyTo;
    	commentData[2] = (String) commentObject.get("updatedAt");
    	commentData[3] = ((Long)commentObject.get("likeCount")).toString();
    	commentData[4] = (String) commentObject.get("textDisplay");
    	return commentData;
	}

	public void parseComments() throws ParseException {
		// Parse the JSON
		JSONParser jsonParser = new JSONParser();

		JSONObject jsonObject = (JSONObject) jsonParser.parse(jsonStrBuf.toString());

		// error check
		// 없는 video ID일 경우 API에서 200 이 아닌, 404 를 던져서 RuntimeException 발생하기 때문에
		// 아래 코드는 200 이지만 다른 오류일때만 사용
		JSONObject errorObject = (JSONObject) jsonObject.get("error");
		if (errorObject != null) {	// error
			System.out.println("code: " + errorObject.get("code"));
			System.out.println("message: " + errorObject.get("message"));
			return;
		}

		// next page 여부
		this.setNextPageToken((String) jsonObject.get("nextPageToken"));	// if no next page, then set to null

		//items의 배열을 추출
        JSONArray itemsArray = (JSONArray) jsonObject.get("items");
        for (int i=0 ; i<itemsArray.size() ; i++) {
        	JSONObject itemObject = (JSONObject) itemsArray.get(i);

        	// top level comment
        	JSONObject snippetObject = (JSONObject) itemObject.get("snippet");
        	JSONObject topLevelObject = (JSONObject) snippetObject.get("topLevelComment");
        	//String commentId = (String) topLevelObject.get("id");
        	JSONObject subSnippetObject = (JSONObject) topLevelObject.get("snippet");

//        	System.out.println("snippet #" + i);
//        	System.out.println(subSnippetObject.get("authorDisplayName"));
//        	System.out.println(subSnippetObject.get("updatedAt"));
//        	System.out.println(subSnippetObject.get("textDisplay"));
        	commentList.add(this.getacomment("", subSnippetObject));

        	// replies
        	JSONObject repliesObject = (JSONObject) itemObject.get("replies");
        	if (repliesObject != null) {
        		String replyTo = (String) subSnippetObject.get("authorDisplayName");
            	JSONArray commentsArray = (JSONArray) repliesObject.get("comments");
            	for (int j=0 ; j<commentsArray.size() ; j++) {
            		//System.out.println("replies #" + j);
            		JSONObject commentObject = (JSONObject) commentsArray.get(j);
            		//String replyId = (String) commentObject.get("id");
            		JSONObject replSnippetObject = (JSONObject) commentObject.get("snippet");
            		//System.out.println(replSnippetObject.get("textDisplay"));
            		commentList.add(this.getacomment(replyTo, replSnippetObject));
            	}
        	}

        }

        jsonStrBuf = null;	// next page를 위한 초기화

	}

	private String getFileName() {
		// file명: videoId#20161028#160212.csv / .xls
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd#HHmmss");
		return getVideoId() + "#" + dateFormat.format(Calendar.getInstance().getTime());
	}

	public void writeCSVFile() throws UnsupportedEncodingException, FileNotFoundException, IOException {
//		commentList = new ArrayList<String[]>();
//		commentList.add(new String[] {"1", "한글 테스트", "comma, test"});
//		commentList.add(new String[] {"2", "quot 테스트", "test \" test"});

		// file명: videoId#20161028#160212.csv
		String CSVFileName = getFileName() + ".csv";

		/**
         * csv 파일을 쓰기위한 설정
         * 설명
         * D:\\test.csv : csv 파일저장할 위치+파일명
         * EUC-KR : 한글깨짐설정을 방지하기위한 인코딩설정(UTF-8로 지정해줄경우 한글깨짐)
         * UTF8으로 하면 맥에서 글자 안 깨지고 모든 나라 언어 잘 나옴. MS Excel에선 깨짐.
         * UTF8으로 저장된 것을 메모장에서 열어서 유니코드로 저장시 외국어 안 깨짐 => MS949를 쓰면 이모티콘만 빼고 모두 해결됨
         * ',' : 배열을 나눌 문자
         * '"' : 값을 감싸주기위한 문자
         **/
		//CSVWriter cw = new CSVWriter(new OutputStreamWriter(new FileOutputStream(CSVFileName), "EUC-KR"),',', '"');
		CSVWriter cw = new CSVWriter(new OutputStreamWriter(new FileOutputStream(CSVFileName), "UTF8"),',', '"');
		//CSVWriter cw = new CSVWriter(new OutputStreamWriter(new FileOutputStream(CSVFileName), "MS949"),',', '"');
		cw.writeAll(commentList);
		cw.close();	// close를 해야만 실제 write가
	}

	public void writeExcelFile() throws FileNotFoundException, IOException {
		HSSFWorkbook workbook = new HSSFWorkbook();
		HSSFSheet sheet = workbook.createSheet();
		HSSFRow row = null;
		HSSFCell cell = null;

		// Move commentList to HSSFSheet
		for (int i=0 ; i<commentList.size() ; i++) {
			row = sheet.createRow(i);
			String[] comments = commentList.get(i);
			for (int j=0 ; j<comments.length ; j++) {
				cell = row.createCell(j);
				cell.setCellValue(comments[j]);
			}
		}

		String excelFileName = getFileName() + ".xls";
		File file = new File(excelFileName);
		FileOutputStream fos = new FileOutputStream(file);
		workbook.write(fos);
		if (workbook != null) workbook.close();
		if (fos != null) fos.close();

	}

	public static void main(String[] args) throws IOException {
		System.out.println("YouTube Comments Crawler ver 0.2.6");	// csv -> excel: 0.1.* -> 0.2.*
		//String fileEncoding=System.getProperty("file.encoding");
		//System.out.println("file.encoding = "+fileEncoding);
		CommentRetriever retriever = new CommentRetriever();

		retriever.getKeyAndConditions();	// api key, max result

		Scanner scanner = new Scanner(System.in);

		// Get the video ID as a String
		System.out.print("Enter YouTube video ID(cyohHyQl-kc): ");
		retriever.setVideoId(scanner.next());
		System.out.println("You entered '" + retriever.getVideoId() + "' for video Id.");

		try {
			System.out.print("How many pages(" + retriever.getMaxResults() + " comments per 1 page): ");
			retriever.setPageNum(scanner.nextInt());
		} catch (InputMismatchException ime) {	// if the input is not integer
			System.out.println("Please enter number only. 1 will be set.");
			retriever.setPageNum(1);
		} finally {
			System.out.println("You entered '" + retriever.getPageNum() + "' for pages.");
		}

		System.out.print("Which order( t for time / r for relevance): ");
		retriever.setOrder(scanner.next());
		System.out.println("You entered '" + retriever.getOrder() + "' for order.");

		System.out.print("Which text format( p for plain text / h for HTML): ");
		retriever.setTextFormat(scanner.next());
		System.out.println("You entered '" + retriever.getTextFormat() + "' for text format.");

		scanner.close();

		try {
			retriever.initCommentList();	// create headers

			for (int i=0 ; i<retriever.getPageNum() ; i++) {
				// get JSON result
				retriever.getComments();

				// get comments by parsing JSON result
				retriever.parseComments();

				// TODO: pagination 구현
				if (retriever.getNextPageToken() == null)
					break;
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {	// error가 나도 이미 받은 정보에 대해서는 파일에 기록한다.
			try {
				// write comments into CSV file
				//retriever.writeCSVFile();

				// write comments into Excel file
				retriever.writeExcelFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

}
