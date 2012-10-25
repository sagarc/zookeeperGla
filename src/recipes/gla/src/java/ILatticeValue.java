
public interface ILatticeValue extends java.io.Serializable {
	
	//class to have access of version of object
	 public class Version{    	
	    	int version;
	    	Version(){version=-1;}
	    	void setVersion(int ver){
	    		version = ver;
	    	}
	    	int getVersion(){
	    		return version;	    	
	    	}
	    }

	 //writes value if version of state matches with oldVersion
	public boolean SetValue (byte[] value, int oldVersion);
		
	//value of state is fetched 	
	abstract byte[] ReadValue(Version version);
	
	//new value from client is proposed using this method. Calls JoinValue and SetValue internally
	abstract void ProposeValue(byte[] proposeValue);
	
	//returns unioun of values, moving up in lattice
	public byte[] JoinValue(byte[] oldByteValue, byte[] proposedByteValue);
	
	
}
