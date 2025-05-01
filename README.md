# 📽️ Syncing Vimeo Videos to AEM DAM Automatically using Custom OSGi Service

This project demonstrates a custom AEM service that integrates with Vimeo to automatically fetch videos and sync them to the AEM DAM (Digital Asset Manager). The service dynamically mirrors the Vimeo project structure, stores metadata, and adds thumbnail renditions—making it seamless for content teams to manage video assets.

---

## 🔧 What Does the Service Do?

The service (`VimeoAemSyncProcess`) performs the following actions:

- ✅ Connects to the **Vimeo API**.
- 📁 Fetches **projects and folders** containing videos.
- 🗂️ Creates a corresponding **folder structure** in the AEM DAM.
- 📝 Uploads **video metadata** to AEM as assets.
- 🖼️ Adds **video thumbnail renditions** from Vimeo.

---

## 🛠️ Key Components

### 📦 OSGi Component

```java
@Component(immediate = true, service = VimeoAemSyncProcessService.class)
```
---

### 🔑 Dependencies
-ResourceResolverFactory: To obtain service-level access to the AEM repository.

-AssetManager: To create and manage assets within the DAM.

-HttpClients: For making RESTful API calls to Vimeo.

-org.json: To parse Vimeo API responses.


## Method Breakdown
### ✅ fetchVimeoVideos()
-Entry point of the service.

-Iterates through a list of Vimeo project IDs.

-Calls Vimeo API to retrieve folders and videos.

-Triggers folder and asset creation inside AEM DAM.

### 📁 createBaseFolderInAEM()
-Creates a top-level folder in the DAM using the Vimeo project ID.

-Ensures no duplication of existing folders.

### 🔁 processVimeoDataRecursively()
-Recursively traverses each folder or video within a Vimeo project.

-Handles nested folders and paginated API responses.

### 📂 createFolderInAEM()
-Creates subfolders inside the project folder in the DAM.

-Updates folder metadata if already present.

### 🎥 createAssetInDam()
-Adds video metadata as a .mp4 placeholder asset in AEM.

-Checks if asset exists, then updates only if the Vimeo modified_time is newer.

-Adds thumbnail renditions for new assets.

### 🧠 updateAssetMetadata() and createNewAsset()
-Updates existing asset metadata with Vimeo properties.

-Creates new assets if not present.

-Handles adding/updating thumbnail renditions.

### 🖼️ setImageRendition()
-Downloads the thumbnail image from Vimeo.

-Adds it as a rendition to the respective asset in AEM DAM.


## Servlet - which calls base method of service.

![image](https://github.com/user-attachments/assets/19a88e46-8cf9-4983-ab1b-129004321563)

