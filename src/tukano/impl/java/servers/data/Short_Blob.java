package tukano.impl.java.servers.data;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Short_Blob {

	@Id
	String shortId;
	@Id
	String blobUrl;

	public Short_Blob() {
	}

	public Short_Blob(String shortId, String blobUrl) {
		super();
		this.shortId = shortId;
		this.blobUrl = blobUrl;
	}

	public String getShortId() {
		return shortId;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
	}

	public String getBlobUrl() {
		return blobUrl;
	}

	public void setBlobUrl(String blobUrl) {
		this.blobUrl = blobUrl;
	}

	@Override
	public int hashCode() {
		return Objects.hash(blobUrl, shortId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Short_Blob other = (Short_Blob) obj;
		return Objects.equals(blobUrl, other.blobUrl) && Objects.equals(shortId, other.shortId);
	}

	@Override
	public String toString() {
		return "Short_Blob [shortId=" + shortId + ", blobUrl=" + blobUrl + "]";
	}

}