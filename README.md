📽️ Syncing Vimeo Videos to AEM DAM Automatically using Custom OSGi Service
In this blog, I'll walk you through a custom AEM service that fetches Vimeo videos and organizes them into the AEM DAM dynamically, maintaining metadata and thumbnail renditions. This integration is especially useful for content teams who frequently manage video content on Vimeo but want to sync it to AEM without manual intervention.

🔧 What Does the Service Do?
The service (VimeoAemSyncProcess) does the following:

Connects to the Vimeo API.

Fetches projects and folders containing videos.

Creates a corresponding folder structure in the AEM DAM.

Uploads video metadata to AEM as assets.

Adds video thumbnail renditions from Vimeo.

🛠️ Key Components
📦 OSGi Component
java
Copy
Edit
@Component(immediate = true, service = VimeoAemSyncProcessService.class)
Registers the class as an OSGi service that implements the VimeoAemSyncProcessService interface.

🔑 Dependencies
ResourceResolverFactory: Used to obtain a service resource resolver.

AssetManager: Used to create assets in the DAM.

HttpClients: Makes API calls to Vimeo.

JSON: Parses API responses.

🔁 Method Breakdown
✅ fetchVimeoVideos()
Entry point of the service.

Iterates through Vimeo project IDs.

Calls Vimeo's API to get folders and items.

Creates corresponding folders in AEM DAM.

📁 createBaseFolderInAEM()
Creates a top-level folder in DAM for each Vimeo project using its ID.

Ensures folders are not duplicated.

🔁 processVimeoDataRecursively()
Recursively processes each folder or video in the project.

Handles nested folders and paginated API responses.

📂 createFolderInAEM()
Creates subfolders under the project folder in AEM DAM.

Also updates the folder title if already exists.

🎥 createAssetInDam()
Adds video metadata as a .mp4 placeholder in AEM.

If the asset exists, it checks for updates based on last modified date from Vimeo.

If the asset is new, it creates it along with thumbnail image rendition.

🧠 updateAssetMetadata() and createNewAsset()
Manages asset metadata updates.

Adds or updates rendition thumbnails.

🖼️ setImageRendition()
Downloads the thumbnail from Vimeo and saves it as a rendition.

🧾 Metadata Mapped
From Vimeo API ➡️ To AEM DAM metadata:

title ➡️ dc:title

description ➡️ aem:vimeoDescription

modified_time ➡️ aem:vimeoLastModified

privacy ➡️ aem:vimeoPrivacy

tags ➡️ aem:vimeoTags

🔐 Vimeo Token
The Vimeo API token is hardcoded:

java
Copy
Edit
private static final String VIMEO_TOKEN = "f234234pa34g23423454223457689";
✅ Note: For production, move this to a secure OSGi config or encrypted vault.

🔄 Ordering in DAM
When a new folder is created, it's moved to the top of its parent folder using orderBefore() for better visibility in the AEM DAM UI.

🔚 Wrapping Up
This service is a great example of how you can integrate external media platforms like Vimeo into your AEM environment. It automates content ingestion and keeps your DAM updated with the latest metadata and structure from Vimeo.

You can schedule this service using a scheduler or trigger it via servlet or workflow.
