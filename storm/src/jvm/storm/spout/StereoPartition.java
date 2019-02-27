package storm.spout;

public class StereoPartition {
	
	public static byte[] mergeStereo(byte[]left, byte[]right) {
		int leftSize = left.length;
		int rightSize = right.length;
		byte[] merge = new byte[leftSize + rightSize];
		System.arraycopy(left, 0, merge, 0, left.length);
		System.arraycopy(right, 0, merge, left.length, right.length);
		return merge;
	}

	public static byte[][] partitionStereo(byte[]merge) {
		int mergeSize = merge.length;
		byte[] left = new byte[mergeSize / 2];
		byte[] right = new byte[mergeSize / 2];
		System.arraycopy(merge, 0, left, 0, left.length);
		System.arraycopy(merge, left.length, right, 0, right.length);
		byte[][] stereo = new byte[2][];
		stereo[0] = left;
		stereo[1] = right;
		return stereo;
	}
}