package com.ftv.player.data.model;

public class Channel {
    private String name;
    private String url;
    private String logoUrl;
    private String category;
    private String epgChannelId;

    public Channel(String name, String url) {
        this.name = name;
        this.url = url;
        this.category = "All";
    }

    public Channel(String name, String url, String logoUrl, String category) {
        this.name = name;
        this.url = url;
        this.logoUrl = logoUrl;
        this.category = category != null ? category : "All";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getEpgChannelId() { return epgChannelId; }
    public void setEpgChannelId(String epgChannelId) { this.epgChannelId = epgChannelId; }
}
