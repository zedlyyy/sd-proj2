package tukano.impl.java.dropBox.msgs;

/**
 * Arguments for listing a folder in Dropbox
 */
public record ListFolderArgs(String path, boolean recursive, boolean include_media_info, boolean include_deleted,
		boolean include_has_explicit_shared_members, boolean include_mounted_folders) {

	/**
	 * Constructor withe the default values
	 * // "include_deleted": false,
	 * // "include_has_explicit_shared_members": false,
	 * // "include_media_info": false,
	 * // "include_mounted_folders": true,
	 * // "include_non_downloadable_files": true,
	 * // "path": "/Homework/math",
	 * // "recursive": false
	 */
	public ListFolderArgs(String path) {
		this(path, false, false, false, false, false);
	}
}
