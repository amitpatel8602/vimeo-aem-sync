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
