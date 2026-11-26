pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Oku"
include(":app")

// Zoom Meeting SDK (uygulama içinde Zoom). mobilertc.aar derleme sırasında yerine konursa eklenir;
// yoksa Oku, Zoom'u telefondaki Zoom uygulamasıyla açar.
if (file("mobilertc/mobilertc.aar").exists()) include(":mobilertc")
