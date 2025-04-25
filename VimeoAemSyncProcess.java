package com.aem.amit.core.service.impl;
 
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
 
import javax.jcr.Node;
 
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
import com.adobe.granite.asset.api.RenditionHandler;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
 
/**
* 
* @author Amit Patel
*
*/
 
@Component(immediate = true, service = VimeoAemSyncProcessService.class)
public class VimeoAemSyncProcess implements VimeoAemSyncProcessService {
 
	private static final String JCR_TITLE = "jcr:title";
	private static final String FOLDER = "folder";
	private static final String DEFAULT_VALUE = "N/A";
	private static final String CONTENT_TYPE_HEADER = "Content-Type";
	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String APPLICATION_JSON = "application/vnd.vimeo.*+json;version=3.4";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String DEFAULT_VIDEO_FORMAT = "video/mp4";
	private static final String PROJECT_ID = "86254569,86254570";
	private static final String AEM_DAM_PATH = "/content/dam/video/vimeo";
	private static final String PROJECT_API = "https://api.vimeo.com/me/projects/";
	private static final String BASE_API = "https://api.vimeo.com";
	private static final String VIMEO_TOKEN = "f234234pa34g23423454223457689";
 
	@Reference
	private ResourceResolverFactory resolverFactory;

 
	private static final Logger LOGGER = LoggerFactory.getLogger(VimeoAemSyncProcess.class);
	
 
	private Map<String, String> pathMap = null;
 
	@Override
	public void fetchVimeoVideos() {
		try {
			for (String url : PROJECT_ID.split(",")) {
				String baseDamPath = AEM_DAM_PATH;
				pathMap = new HashMap<>();
				String projectUrl = PROJECT_API + url;
				String newBasePath = createBaseFolderInAem(projectUrl, baseDamPath);
				processVimeoData(projectUrl + "/items", extractVimeoId(projectUrl), newBasePath);
			}
		} catch (Exception e) {
			handleException("Error in fetchVimeoVideos: " + e.getMessage(), e);
		}
	}
 
	private String createBaseFolderInAem(String apiUrl, String baseDamPath) {
		String folderPath = null;
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet getRequest = createHttpGetRequest(apiUrl);
 
			try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
				int statusCode = response.getStatusLine().getStatusCode();
 
				if (statusCode == 200) {
					String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
					JSONObject folderData = new JSONObject(responseString);
 
					String folderName = folderData.optString("name", "default-folder");
					String folderUri = folderData.optString("uri", null);
 
					if (StringUtils.isBlank(folderUri)) {
						LOGGER.error("Folder URI is missing or invalid in the API response: {}", responseString);
						return null;
					}
 
					try (ResourceResolver resourceResolver = JCRUtils.getResourceResolverFromFactory(resolverFactory)) {
						folderPath = baseDamPath + "/" + extractVimeoId(folderUri);
 
						Resource parentResource = resourceResolver.getResource(folderPath);
						if (parentResource == null) {
							Map<String, Object> folderProperties = new HashMap<>();
							folderProperties.put("jcr:primaryType", "sling:OrderedFolder");
							folderProperties.put(JCR_TITLE, folderName);
 
							Resource newFolder = resourceResolver.create(resourceResolver.getResource(baseDamPath),
									extractVimeoId(folderUri), folderProperties);
							LOGGER.info("Base folder created successfully in AEM: {}", newFolder.getPath());
							resourceResolver.commit();
						} else {
							LOGGER.info("Base folder already exists in AEM: {}", folderPath);
						}
 
						pathMap.put(extractVimeoId(folderUri), folderPath);
					}
				} else {
					LOGGER.error("Failed to fetch folder data from API. HTTP Status: {} for URL: {}", statusCode,
							apiUrl);
				}
			}
		} catch (Exception e) {
			handleException("Error in createBaseFolderInAem: " + e.getMessage(), e);
		}
		return folderPath;
	}
	
	private void processVimeoData(String apiUrl, String folderId, String parentFolderPath) {
		try (CloseableHttpClient httpClient = HttpClients.custom().build()) {
			HttpGet get = createHttpGetRequest(apiUrl);
			try (CloseableHttpResponse response = httpClient.execute(get)) {
				handleResponse(response, folderId, parentFolderPath);
			}
		} catch (Exception e) {
			handleException("Error in processVimeoData: " + e.getMessage(), e);
		}
	}
 
	private void handleResponse(CloseableHttpResponse response, String folderId, String parentFolderPath)
			throws IOException, JSONException {
		if (response.getStatusLine().getStatusCode() == 200) {
			String responseString = EntityUtils.toString(response.getEntity());
			JSONObject jsonObject = new JSONObject(responseString);
			processJsonObject(jsonObject, folderId, parentFolderPath);
		} else {
			handleException(
					String.format("Failed to fetch data. HTTP Status: %s", response.getStatusLine().getStatusCode()),
					null);
		}
	}
 
	private void processJsonObject(JSONObject jsonObject, String folderId, String parentFolderPath)
			throws JSONException {
		if (!jsonObject.has("data") || jsonObject.getJSONArray("data").length() == 0) {
			LOGGER.info("No 'data' attribute or empty data. Stopping recursion.");
			return;
		}
 
		JSONArray dataArray = jsonObject.getJSONArray("data");
		processDataArray(dataArray, folderId, parentFolderPath);
 
		handlePagination(jsonObject, folderId, parentFolderPath);
	}
 
	private void processDataArray(JSONArray dataArray, String folderId, String parentFolderPath) throws JSONException {
		for (int i = 0; i < dataArray.length(); i++) {
			JSONObject dataObject = dataArray.getJSONObject(i);
			String type = dataObject.optString("type", DEFAULT_VALUE);
 
			if (FOLDER.equalsIgnoreCase(type)) {
				processFolder(dataObject, parentFolderPath);
			} else if ("video".equalsIgnoreCase(type)) {
				createAssetInDam(dataObject, folderId);
			}
		}
	}
 
	private void processFolder(JSONObject dataObject, String parentFolderPath) {
		try {
			String folderPath = createFolderInAem(dataObject, parentFolderPath);
 
			JSONObject folderJson = dataObject.getJSONObject(FOLDER);
			String folderUri = folderJson.optString("uri", null);
			if (folderUri != null) {
				String newFolderId = extractVimeoId(folderUri);
				pathMap.put(newFolderId, folderPath);
				processVimeoData(BASE_API + folderUri + "/items", newFolderId,
						folderPath);
			}
		} catch (JSONException e) {
			LOGGER.info("Exception raised in processFolder method", e);
		}
	}
 
	private void handlePagination(JSONObject jsonObject, String folderId, String parentFolderPath)
			throws JSONException {
		if (jsonObject.has("paging")) {
			JSONObject pagingObject = jsonObject.getJSONObject("paging");
			String nextPageUrl = pagingObject.optString("next", null);
			if (nextPageUrl != null) {
				processVimeoData(BASE_API + nextPageUrl, folderId,
						parentFolderPath);
			}
		}
	}
 
	private String createFolderInAem(JSONObject folderData, String parentFolderPath) {
		try (ResourceResolver resourceResolver = JCRUtils.getResourceResolverFromFactory(resolverFactory)) {
			JSONObject folderJson = folderData.getJSONObject(FOLDER);
			String folderName = folderJson.optString("name", DEFAULT_VALUE);
			String folderUri = folderJson.optString("uri", DEFAULT_VALUE);
 
			String folderPath = parentFolderPath + "/" + extractVimeoId(folderUri);
 
			Resource parentResource = resourceResolver.getResource(parentFolderPath);
			if (parentResource == null) {
				LOGGER.error("Parent folder does not exist at: {}", parentFolderPath);
				return null;
			}
 
			Resource folderResource = resourceResolver.getResource(folderPath);
			if (folderResource == null) {
				// Folder does not exist, create a new one
				Map<String, Object> folderProperties = new HashMap<>();
				folderProperties.put("jcr:primaryType", "sling:OrderedFolder");
				folderProperties.put(JCR_TITLE, folderName);
				Resource newFolder = resourceResolver.create(resourceResolver.getResource(parentFolderPath),
						extractVimeoId(folderUri), folderProperties);
				LOGGER.info("Folder created successfully at: {}", newFolder.getPath());
 
				// Reorder the folder to appear at the top
				reorderFolderToTop(parentResource, newFolder.getName());
 
				resourceResolver.commit();
			} else {
				// Folder exists, check if the title needs to be updated
				ModifiableValueMap folderProperties = folderResource.adaptTo(ModifiableValueMap.class);
				if (folderProperties != null) {
					String existingTitle = folderProperties.get(JCR_TITLE, String.class);
					if (!folderName.equals(existingTitle)) {
						folderProperties.put(JCR_TITLE, folderName);
						resourceResolver.commit();
						LOGGER.info("Folder title updated successfully at: {}", folderPath);
					} else {
						LOGGER.info("Folder already exists with the same title at: {}", folderPath);
					}
				}
			}
 
			return folderPath;
		} catch (Exception e) {
			handleException("Error in createAssetInDam: " + e.getMessage(), e);
		}
		return null;
	}
	
	private void reorderFolderToTop(Resource parentResource, String folderName) {
		try {
			Node parentNode = parentResource.adaptTo(Node.class);
			if (parentNode != null && parentNode.isNodeType("sling:OrderedFolder")) {
				if (parentNode.hasNode(folderName)) {
					Node folderNode = parentNode.getNode(folderName);
					parentNode.orderBefore(folderNode.getName(), parentNode.getNodes().nextNode().getName());
					LOGGER.info("Reordered folder '{}' to the top in parent folder '{}'", folderName,
							parentNode.getPath());
				}
			} else {
				LOGGER.warn("Parent folder '{}' is not of type 'sling:OrderedFolder'. Reordering skipped.",
						parentResource.getPath());
			}
		} catch (Exception e) {
			LOGGER.error("Error while reordering folder '{}' in parent folder '{}': {}", folderName,
					parentResource.getPath(), e.getMessage(), e);
		}
	}
 
	private void createAssetInDam(JSONObject dataObject, String folderId) throws JSONException {
		JSONObject vidJson = dataObject.getJSONObject("video");
		String baseLink = null;
		String uri = vidJson.optString("uri", DEFAULT_VALUE);
		String name = vidJson.optString("name", DEFAULT_VALUE);
		String description = vidJson.optString("description", DEFAULT_VALUE);
		String vimeoLastModified = vidJson.optString("modified_time", DEFAULT_VALUE);
		String[] vimeoTags = extractTags(vidJson);
		String privacy = extractPrivacy(vidJson);
		if (vidJson.has("pictures")) {
			JSONObject picturesObject = vidJson.getJSONObject("pictures");
			baseLink = picturesObject.optString("base_link", null);
		}
 
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("dc:title", name);
		metadata.put("aem:vimeoDescription", description);
		metadata.put("aem:vimeoId", extractVimeoId(uri));
		metadata.put("dc:format", DEFAULT_VIDEO_FORMAT);
		metadata.put("aem:vimeoLastModified", vimeoLastModified);
		metadata.put("aem:vimeoPrivacy", privacy);
		if (vimeoTags.length != 0) {
			metadata.put("aem:vimeoTags", vimeoTags);
		}
		try (ResourceResolver resourceResolver = JCRUtils.getResourceResolverFromFactory(resolverFactory)) {
			AssetManager assetManager = resourceResolver.adaptTo(AssetManager.class);
			if (assetManager == null) {
				LOGGER.error("AssetManager is null. Cannot create or update asset.");
				return;
			}
 
			String assetPath = pathMap.get(folderId) + "/" + extractVimeoId(uri) + ".mp4";
			Resource assetResource = resourceResolver.getResource(assetPath);
 
			if (assetResource != null) {
				updateAssetMetadata(resourceResolver, assetResource, metadata, vimeoLastModified, assetPath, baseLink);
			} else {
				createNewAsset(resourceResolver, assetManager, assetPath, metadata, baseLink);
			}
		} catch (Exception e) {
			LOGGER.error("Error while creating or updating asset in DAM: {}", e.getMessage(), e);
		}
	}
 
	private String extractPrivacy(JSONObject vidJson) throws JSONException {
		JSONObject privacyObject = vidJson.getJSONObject("privacy");
		return privacyObject.optString("embed", DEFAULT_VALUE);
	}
 
	private String[] extractTags(JSONObject dataObject) {
 
		try {
			JSONArray tagArray = dataObject.getJSONArray("tags");
			String[] tag = new String[tagArray.length()];
			for (int i = 0; i < tagArray.length(); i++) {
				JSONObject tagObject = tagArray.getJSONObject(i);
				tag[i] = tagObject.getString("tag");
			}
			return tag;
		} catch (Exception e) {
			LOGGER.error("Error while fetching tags: ", e);
		}
		return new String[0];
	}
 
	private void updateAssetMetadata(ResourceResolver resourceResolver, Resource assetResource,
			Map<String, Object> metadata, String vimeoLastModified, String assetPath, String baseLink) {
		try {
			Resource metadataResource = assetResource.getChild("jcr:content/metadata");
			if (metadataResource != null) {
				ModifiableValueMap existingMetadata = metadataResource.adaptTo(ModifiableValueMap.class);
				if (existingMetadata != null) {
					String existingLastModified = existingMetadata.get("aem:vimeoLastModified", String.class);
					if (vimeoLastModified.equals(existingLastModified)) {
						LOGGER.info("Asset metadata matches API data. Skipping metadata update for: {}", assetPath);
					} else {
						LOGGER.info("Metadata has changed. Updating metadata for asset: {}", assetPath);
						metadata.entrySet().stream().filter(entry -> !DEFAULT_VALUE.equals(entry.getValue()))
								.forEach(entry -> existingMetadata.put(entry.getKey(), entry.getValue()));
						// Set image rendition if metadata has changed
						Asset asset = assetResource.adaptTo(Asset.class);
						if (asset != null && StringUtils.isNotBlank(baseLink)) {
							setImageRendition(asset, baseLink);
						}
						resourceResolver.commit();
						LOGGER.info("Metadata updated successfully for asset: {}", assetPath);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while updating metadata for asset: {}", e.getMessage(), e);
		}
	}
 
	private void createNewAsset(ResourceResolver resourceResolver, AssetManager assetManager, String assetPath,
			Map<String, Object> metadata, String baseLink) {
		try {
			Asset asset = assetManager.createAsset(assetPath, null, DEFAULT_VIDEO_FORMAT, true);
 
			Resource metadataResource = resourceResolver.getResource(asset.getPath() + "/jcr:content/metadata");
			if (metadataResource != null) {
				ModifiableValueMap metadataMap = metadataResource.adaptTo(ModifiableValueMap.class);
				if (metadataMap != null) {
					metadata.entrySet().stream().filter(entry -> !DEFAULT_VALUE.equals(entry.getValue()))
							.forEach(entry -> metadataMap.put(entry.getKey(), entry.getValue()));
				}
			}
			if (asset != null && StringUtils.isNotBlank(baseLink)) {
				setImageRendition(asset, baseLink);
			}
			resourceResolver.commit();
			LOGGER.info("Video asset created successfully at: {}", asset.getPath());
		} catch (Exception e) {
			LOGGER.error("Error while creating new asset: {}", e.getMessage(), e);
		}
	}
 
	private void setImageRendition(Asset asset, String url) throws IOException {
		Map<String, Object> videoSourceMap = new HashMap<>();
		try (BufferedInputStream inputStream = new BufferedInputStream(new URL(url).openStream());) {
			videoSourceMap.put(RenditionHandler.PROPERTY_RENDITION_MIME_TYPE, "image/png");
			asset.addRendition("vimeo_thumbnail.png", inputStream, videoSourceMap);
		}
	}
 
	private String extractVimeoId(String uri) {
		String newUrl = uri.replaceAll("/items.*", "");
		if (StringUtils.isNotBlank(newUrl) && newUrl.contains("/")) {
			String[] parts = newUrl.split("/");
			return parts[parts.length - 1];
		}
		return "unknown";
	}
 
	private void handleException(String errorMessage, Exception e) {
		LOGGER.error(errorMessage, e);
	}
 
	private HttpGet createHttpGetRequest(String apiUrl) throws URISyntaxException {
		URIBuilder builder = new URIBuilder(apiUrl);
		HttpGet get = new HttpGet(builder.build());
		get.addHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON);
		get.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + VIMEO_TOKEN);
		return get;
	}
}
