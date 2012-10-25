
public interface ILatticeValue {
	public void ProposeValue(byte[] value);
	public byte[] JoinValue(byte[] oldByteValue, byte[] proposedByteValue);
	public byte[] ReadValue();
}
