package zkGla;

public interface ILatticeValue {
	
	//class to have access of version of object
	 public class Version{    	
	    	int version;
	    	public Version(){version=-1;}
	    	public void setVersion(int ver){
	    		version = ver;
	    	}
	    	int getVersion(){
	    		return version;	    	
	    	}
	    }

	 //writes value if version of state matches with oldVersion
	public boolean SetValue (byte[] value, int oldVersion, String nodeName);
		
	//value of state is fetched 	
	public byte[] ReadValue(Version version, String nodeName);
	
	public byte[] ReadValue(String nodeName);
	
	//new value from client is proposed using this method. Calls JoinValue and SetValue internally
	abstract void ProposeValue(byte[] proposeValue);
	
	//returns unioun of values, moving up in lattice
	abstract byte[] JoinValue(byte[] oldByteValue, byte[] proposedByteValue);
	
	
}
