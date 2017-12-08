package retrieval.helper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import system.model.DocumentIdMapperModel;
import system.model.DocumentRankModel;
import system.model.DocumentTermModel;
import system.model.IndexModel;
import system.model.QueryModel;
import system.model.QueryResultModel;
import system.model.TermIndexModel;

public class RetrievalHelper {

	private static Map<Integer, String> docIdMap = new HashMap<>();
	private static Map<Integer, Integer> docLengthMap = new HashMap<>();
	private static Logger LOGGER = Logger.getLogger(RetrievalHelper.class.getName());
	private static Map<String, List<IndexModel>> unaryIndexMap = new HashMap<>();
	private static Map<Integer, List<DocumentTermModel>> docTermFreqMap = new HashMap<>();
	private static double IDF_DEFAULT = 1.5;

	private RetrievalHelper() {
		// do nothing;
	}

	public static void initHelper(String fileLocation, String indexFileLocation) {
		try {
			initMap(fileLocation);
			readUnaryIndex(indexFileLocation);
		} catch (Exception e) {
			LOGGER.severe("Failed initializing helper");
			e.printStackTrace();
		}
	}

	private static <T> void PrintToFile(List<T> table, String fileLocation, String fileName) {
		try {
			// LOGGER.info("Printing table" + fileName + "\tTotal number of
			// unique words = "
			// + table.size());
			writeToJsonStream(fileLocation, fileName, table);
		} catch (Exception e) {
			LOGGER.severe("Error writing file" + e.getMessage());
			throw e;
		}

	}

	public static <T> void writeToJsonStream(String fileLoction, String fileName, List<T> tfTable) {
		try {
			Gson gson = new Gson();
			LOGGER.info("Writing json data to file " + fileLoction + "/" + fileName);
			JsonWriter writer = new JsonWriter(
					new OutputStreamWriter(new FileOutputStream(new File(fileLoction + "/" + fileName))));
			writer.setIndent("  ");
			writer.beginArray();
			for (T tfModel : tfTable) {
				gson.toJson(tfModel, tfModel.getClass(), writer);
			}

			writer.endArray();
			writer.close();
		} catch (Exception e) {
			LOGGER.severe("Error writing file");
			e.printStackTrace();
		}

	}

	public static <T> void writeToJsonStream(String fileLoction, String fileName, T data) {
		try {
			Gson gson = new Gson();
			LOGGER.info("Writing json data to file " + fileLoction + "/" + fileName);
			JsonWriter writer = new JsonWriter(
					new OutputStreamWriter(new FileOutputStream(new File(fileLoction + "/" + fileName))));
			writer.setIndent("  ");
			writer.beginArray();
			gson.toJson(data, data.getClass(), writer);
			writer.endArray();
			writer.close();
		} catch (Exception e) {
			LOGGER.severe("Error writing file");
			e.printStackTrace();
		}
	}

	public static List<QueryModel> getQueryList(String fileName) throws FileNotFoundException, IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			List<QueryModel> queryList = new ArrayList<>();
			System.out.println("Reading file: " + fileName);
			String currentLine;
			while ((currentLine = br.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(currentLine);
				QueryModel query = new QueryModel();
				if (st.hasMoreTokens()) {
					query.setId(Integer.parseInt(st.nextToken()));
				}
				StringBuilder sb = new StringBuilder();
				while (st.hasMoreTokens()) {
					sb.append(st.nextToken() + " ");
				}
				System.out.println("query obtained is " + sb.toString().trim());
				query.setQuery(sb.toString().trim());
				queryList.add(query);
			}
			return queryList;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	public static void initMap(String fileLocation) {
		if (docIdMap.size() == 0) {
			Path path = Paths.get(fileLocation);
			int count = 1;
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
				for (Path entry : stream) {
					// if (entry.toFile().isFile()) {
					if (entry.toFile().isFile() && (getFileExtension(entry.toFile()).equals("txt"))) {
						try (Stream<String> fileStream = Files.lines(entry)) {
							// Integer docId =
							// getDocId(entry.getFileName().toString());
							Iterator<String> iterator = fileStream.iterator();
							while (iterator.hasNext()) {
								String nextLine = iterator.next();
								String[] split = nextLine.split("\\s+");
								docLengthMap.put(count, split.length);
							}
						}
						docIdMap.put(count++, entry.getFileName().toString());

					}
				}
			} catch (Exception e) {
				LOGGER.severe("Error in initializing doc id map");
			}
			LOGGER.info("Init done...!!!!     Total Documents read = " + docIdMap.size());
			PrintToFile(getDocMapperList(), getParentfileLocation(fileLocation), "docId_Map.json");
		}
	}

	private static List<DocumentIdMapperModel> getDocMapperList() {
		List<DocumentIdMapperModel> docIdMapper = new ArrayList<>();
		for (Map.Entry<Integer, String> entry : docIdMap.entrySet()) {
			DocumentIdMapperModel model = new DocumentIdMapperModel();
			model.setDocId(entry.getKey());
			model.setDocName(entry.getValue());
			docIdMapper.add(model);
		}
		return docIdMapper;
	}

	public static Integer getDocId(String docName) {
		Integer docId = null;
		for (Map.Entry<Integer, String> entry : docIdMap.entrySet()) {
			if (entry.getValue().equals(docName)) {
				docId = entry.getKey();
				break;
			}
		}
		if (docId == null) {
			LOGGER.warning("doc id for this document not found" + docName);
		}
		return docId;
	}

	public static String getParentfileLocation(String fileLocation) {
		// System.out.println("file location = "+fileLocation);
		File f = new File(fileLocation);
		System.out.println("file path=" + f.getAbsolutePath());
		return f.getParent();
	}

	public static Map<String, List<IndexModel>> readJsonStream(String fileLocation) throws IOException {
		try {
			// LOGGER.info("reading file");
			Gson gson = new Gson();
			JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(new File(fileLocation))));
			Map<String, List<IndexModel>> indexMap = new HashMap<>();
			reader.beginArray();
			while (reader.hasNext()) {

				TermIndexModel termIndex = gson.fromJson(reader, TermIndexModel.class);
				// LOGGER.info(""+termIndex.getTerm()+"\t
				// "+termIndex.getInvertedList());
				indexMap.put(termIndex.getTerm(), termIndex.getInvertedList());
			}
			reader.endArray();
			reader.close();
			// LOGGER.info("" + indexMap.size());
			return indexMap;
		} catch (IOException e) {
			LOGGER.severe("\n\n\t!!!...Not able to find index files at the given location :" + fileLocation + "\n\n");
			LOGGER.severe("" + e.getMessage());
			throw e;
		}
	}

	public static void readUnaryIndex(String indexedFileLocation) throws IOException {
		if (unaryIndexMap.size() == 0) {
			unaryIndexMap = readJsonStream(indexedFileLocation);
			System.out.println("\n\n\n\n\ncreating doc term map\n\n\n");
			docTermFreqMap = getDocTermMap(unaryIndexMap);
		}
	}

	public static List<IndexModel> getInvertedIndex(String term) {
		if (unaryIndexMap.containsKey(term))
			return unaryIndexMap.get(term);
		else
			return new ArrayList<>();
	}

	public static Map<String, List<IndexModel>> getIndex() {
		return unaryIndexMap;
	}

	private static String getFileExtension(File file) {
		String name = file.getName();
		try {
			// System.out.println("extension =" +
			// name.substring(name.lastIndexOf(".") +
			// 1));
			return name.substring(name.lastIndexOf(".") + 1);
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * 
	 * @param docId
	 * @return document length You might want to cast it into double for
	 *         caluclations
	 */
	public static int getDocLenth(int docId) {
		return docLengthMap.get(docId);
	}

	public static double getAvgDocLength() {
		int totalDocLength = 0;
		for (Map.Entry<Integer, Integer> entry : docLengthMap.entrySet()) {
			totalDocLength += entry.getValue();
		}
		return (double) totalDocLength / docLengthMap.size();
	}

	public static int getCollectionSize() {
		return docLengthMap.size();
	}

	/**
	 * 
	 * @param queryResult
	 * @param filename
	 * @param fileLocation
	 * @param resultType
	 */
	public static void printIndex(List<QueryResultModel> queryResult, String fileLocation, String resultType) {

		// query_id Q0 doc_id rank BM25_score system_name

		for (QueryResultModel model : queryResult) {
			int rank = 1;
			StringBuilder sb = new StringBuilder();
			for (DocumentRankModel docRankModel : model.getResults()) {
				sb.append(model.getQueryId() + " Q0 ");
				sb.append(docRankModel.getDocId() + " ");
				sb.append(rank + " " + docRankModel.getRankScore() + " ");
				sb.append(resultType + "\n");
				rank++;
			}
			writeToFile(sb.toString(), resultType + "_" + model.getQueryId(), fileLocation);
		}

	}

	public static void writeToFile(String doc, String filename, String fileLocation) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(fileLocation + "/" + filename + ".txt"));
			writer.write(doc);

		} catch (IOException e) {
		} finally {
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e) {
			}
		}

	}

	public static double getTermFreqInDoc(String term, int docId) {
		int tf = 0;
		List<IndexModel> invetedIndex = RetrievalHelper.getInvertedIndex(term);
		for (IndexModel model : invetedIndex) {
			if (model.getDocId() == docId) {
				tf = model.getTf();
			}
		}
		return tf;
	}

	public static double getIdf(String word) {
		if (RetrievalHelper.getInvertedIndex(word) != null) {
			double noOfdocs = RetrievalHelper.getInvertedIndex(word).size();
			double totalDocs = RetrievalHelper.getCollectionSize();
			return Math.log((noOfdocs) / (totalDocs));
		} else {
			return IDF_DEFAULT;
		}
	}

	public static double calculateQueryFrequency(String[] queryTerms, String term) {
		double freq = 0;
		for (String nextTerm : queryTerms) {
			if (nextTerm.equals(term))
				freq++;
		}
		return freq;
	}
	
	
	private static Map<Integer, List<DocumentTermModel>> getDocTermMap(Map<String, List<IndexModel>> unaryIndexMap) {
		Map<Integer, List<DocumentTermModel>> docTermMap = new HashMap<>();
		for(Map.Entry<String, List<IndexModel>> entry : unaryIndexMap.entrySet()) {
			List<IndexModel> invertedIndex = entry.getValue();
			Iterator<IndexModel> itr = invertedIndex.iterator();

			while(itr.hasNext()) {
				IndexModel indexModel = itr.next();
				if(docTermMap.containsKey(indexModel.getDocId())) {
					List<DocumentTermModel> list = docTermMap.get(indexModel.getDocId());
					DocumentTermModel model =new DocumentTermModel();
					model.setTerm(entry.getKey());
					model.setTf(indexModel.getTf());
					list.add(model);
					docTermMap.put(indexModel.getDocId(), list);
				}else {
					List<DocumentTermModel> list = new ArrayList<>();
					DocumentTermModel model =new DocumentTermModel();
					model.setTerm(entry.getKey());
					model.setTf(indexModel.getTf());
					list.add(model);
					docTermMap.put(indexModel.getDocId(), list);
				}
			}
		}
		//sort the map values
		for(Map.Entry<Integer, List<DocumentTermModel>> entry: docTermMap.entrySet()) {
			docTermMap.put(entry.getKey(), entry.getValue().stream().sorted().collect(Collectors.toList()));
		}
		LOGGER.info(""+docTermMap);
		return docTermMap;
		
	}
	
	/*
	 * this method returns the terms in a document in sorted order of their frequency 
	 */
	public static List<DocumentTermModel> getTermFreqMap(int docId){
		return docTermFreqMap.get(docId);
	}
	
	

}
