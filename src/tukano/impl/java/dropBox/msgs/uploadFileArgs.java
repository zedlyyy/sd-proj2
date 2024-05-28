package tukano.impl.java.dropBox.msgs;

/**
 * Arguments for uploading a file to Dropbox
 */
public record uploadFileArgs(boolean autorename, String mode, boolean mute, String path, boolean strict_conflict) {

    /**
     * Constructors with default values:
     * "autorename": false,
     * "mode": "add",
     * "mute": false,
     * "path": "/Homework/math/Matrices.txt",
     * "strict_conflict": false
     */
    public uploadFileArgs(boolean autorename, String mode, boolean mute, String path, boolean strict_conflict) {
        this.autorename = autorename;
        this.mode = mode;
        this.mute = mute;
        this.path = path;
        this.strict_conflict = strict_conflict;
    }

    public uploadFileArgs(String path) {
        this(false, "add", false, path, false);
    }

}
