package ftp.sh.sierramapssdk;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SierraMapsView extends WebView {
    private static final String BASE_URL = "https://sierramaps.ftp.sh/api";
    private static final String TILE_URL = "https://sierramapstiles.onrender.com/tiles/{x}/{y}/{z}";
    private String apiKey = "";

    public SierraMapsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeWebView();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    private void initializeWebView() {
        WebSettings webSettings = getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return handleOfflineTiles(request.getUrl().toString());
            }
        });

        loadMap();
    }

    private void loadMap() {
        String htmlContent = "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<link rel='stylesheet' href='https://unpkg.com/maplibre-gl/dist/maplibre-gl.css' />" +
                "<script src='https://unpkg.com/maplibre-gl/dist/maplibre-gl.js'></script>" +
                "</head><body>" +
                "<div id='map' style='width:100vw; height:100vh;'></div>" +
                "<script>" +
                "   var map = new maplibregl.Map({" +
                "       container: 'map'," +
                "       style: {" +
                "           version: 8," +
                "           sources: {" +
                "               'sierra-tiles': {" +
                "                   type: 'raster'," +
                "                   tiles: ['" + TILE_URL + "']," +
                "                   tileSize: 256" +
                "               }" +
                "           }," +
                "           layers: [{" +
                "               id: 'sierra-layer'," +
                "               type: 'raster'," +
                "               source: 'sierra-tiles'" +
                "           }]" +
                "       }," +
                "       center: [0, 0]," +
                "       zoom: 5" +
                "   });" +
                "</script>" +
                "</body></html>";

        loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
    }

    private WebResourceResponse handleOfflineTiles(String url) {
        try {
            File cacheDir = new File(getContext().getCacheDir(), "tiles");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String filename = url.replaceAll("[^a-zA-Z0-9]", "_");
            File cacheFile = new File(cacheDir, filename);

            if (cacheFile.exists()) {
                return new WebResourceResponse("image/png", "UTF-8", new FileInputStream(cacheFile));
            } else {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.connect();
                FileOutputStream fos = new FileOutputStream(cacheFile);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    fos.write(connection.getInputStream().readAllBytes());
                }
                fos.close();
                return new WebResourceResponse("image/png", "UTF-8", new FileInputStream(cacheFile));
            }
        } catch (Exception e) {
            return null;
        }
    }

    public void addMarker(double lat, double lng, String title) {
        evaluateJavascript("var marker = new maplibregl.Marker().setLngLat([" + lng + ", " + lat + "]).setPopup(new maplibregl.Popup().setText('" + title + "')).addTo(map);", null);
    }

    public void addCustomMarker(double lat, double lng, String iconUrl) {
        evaluateJavascript("var el = document.createElement('div'); el.style.backgroundImage = 'url(" + iconUrl + ")'; el.style.width = '30px'; el.style.height = '30px';" +
                "new maplibregl.Marker(el).setLngLat([" + lng + ", " + lat + "]).addTo(map);", null);
    }

    public void setTilt(int angle) {
        evaluateJavascript("map.setPitch(" + angle + ");", null);
    }

    public void setRotation(int angle) {
        evaluateJavascript("map.setBearing(" + angle + ");", null);
    }

    public void drawRoute(String routeUrl) {
        evaluateJavascript("fetch('" + routeUrl + "') " +
                ".then(res => res.json()).then(data => {" +
                "var route = data.routes[0].geometry.coordinates.map(coord => [coord[1], coord[0]]);" +
                "map.addLayer({ 'id': 'route', 'type': 'line', 'source': { 'type': 'geojson', 'data': { 'type': 'Feature', 'geometry': { 'type': 'LineString', 'coordinates': route } } }, 'paint': { 'line-color': '#007bff', 'line-width': 4 } });" +
                "});", null);
    }

    // 🌍 Places API: Search for places
    public void searchPlace(String query, PlacesCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/places?query=" + query + "&apiKey=" + apiKey);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                String response = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    response = new String(connection.getInputStream().readAllBytes());
                }
                callback.onPlacesResult(response);
            } catch (Exception e) {
                callback.onPlacesError(e);
            }
        }).start();
    }

    // 📍 Get place details by place_id
    public void getPlaceDetails(String placeId, PlacesCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/places/" + placeId + "/?apiKey=" + apiKey);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                String response = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    response = new String(connection.getInputStream().readAllBytes());
                }
                callback.onPlacesResult(response);
            } catch (Exception e) {
                callback.onPlacesError(e);
            }
        }).start();
    }

    // 🚗 Routing API
    public void getRoute(String start, String end, int maxSolutions, RouteCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/route/" + start + "/" + end + "/" + maxSolutions + "/?apiKey=" + apiKey);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                String response = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    response = new String(connection.getInputStream().readAllBytes());
                }
                callback.onRouteResult(response);
            } catch (Exception e) {
                callback.onRouteError(e);
            }
        }).start();
    }

    public void getRouteWithWaypoints(String routePath, int maxSolutions, RouteCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/route/" + routePath + "/" + maxSolutions + "/?apiKey=" + apiKey);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                String response = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    response = new String(connection.getInputStream().readAllBytes());
                }
                callback.onRouteResult(response);
            } catch (Exception e) {
                callback.onRouteError(e);
            }
        }).start();
    }

    public interface PlacesCallback {
        void onPlacesResult(String result);
        void onPlacesError(Exception e);
    }

    public interface RouteCallback {
        void onRouteResult(String result);
        void onRouteError(Exception e);
    }
}
