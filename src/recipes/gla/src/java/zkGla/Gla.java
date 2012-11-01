package zkGla;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;


import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import zkGla.ILatticeValue.Version;




abstract class Gla implements Watcher, ILatticeValue, java.io.Serializable {

    ZooKeeper zk = null;
    static Integer mutex;    
    public static String root = "/gla";
    
    Gla(){};

    Gla(String address) {    	
    	
        if(zk == null){
            try {
                System.out.println("Starting ZK:");
                zk = new ZooKeeper(address, 3000, this);                
                System.out.println("Finished starting ZK: " + zk);
                //root = "/gla";
            } catch (IOException e) {
                System.out.println(e.toString());                
            }
        }    
    }
    
    synchronized public void process(WatchedEvent event) {
        //Process events
    }
    
    public abstract byte[] JoinValue(byte[] ov, byte[] nv);
    
    public abstract void ProposeValue(byte[] proposeValue);
    
    public byte[] ReadValue(String nodeName){
    	if (zk != null) {
    		Stat s = null;
			try {
				//System.out.println("root:" + root);
				s = zk.exists(nodeName, false);
			} catch (KeeperException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    		if(s==null) return null;
    		byte[] byteValue;
    		Stat oldStat = s;
    		try {
				byteValue = zk.getData(nodeName,false, oldStat);				
				return byteValue;
			} catch (KeeperException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		return null;    		
    	}
    	return null;
    }
    
    public byte[] ReadValue(Version version, String nodeName){
    	if (zk != null) {
    		Stat s = null;
			try {
				s = zk.exists(nodeName, false);
			} catch (KeeperException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    		if(s==null) return null;
    		byte[] byteValue;
    		Stat oldStat = s;
    		try {
				byteValue = zk.getData(nodeName,false, oldStat);
				version.setVersion(oldStat.getVersion());
				return byteValue;
			} catch (KeeperException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		return null;    		
    	}
    	return null;
    }
  
    public boolean SetValue(byte[] value, int oldVersion, String nodeName){
    	if (zk != null) {
	        try {	        	
	        	                
	            Stat s = zk.exists(nodeName, false);
	            if (s == null) {
	                zk.create(nodeName, value, Ids.OPEN_ACL_UNSAFE,
	                        CreateMode.PERSISTENT); 
	                return true;
	            }
	            else{	            	
	            	Stat newStat= s;
	            	int newVersion;    		            			                		                	
                	newStat = zk.setData(nodeName,value,oldVersion);
                	if(newStat!=null) return true;
                		//newVersion = newStat.getVersion();	                	
                	//if(newVersion == oldVersion+1) return true;
                	return false;
	            }                       
	       } catch (KeeperException e) {
	            System.out
	                    .println("Keeper exception when instantiating gla: "
	                            + e.toString());
	        } catch (InterruptedException e) {
	            System.out.println("Interrupted exception");
	        }
	        return false;
	    }
    	return false;
    }
        
    public boolean TestCreateZnode(byte[] initialValue, String nodeName){
    	if (zk != null) {
	        try {        	 
	        	//System.out.println(zk.toString());
	            Stat s = zk.exists(nodeName, false);
	            if (s == null) {	            	
	                zk.create(nodeName, initialValue, Ids.OPEN_ACL_UNSAFE,
	                        CreateMode.PERSISTENT);
	                System.out.println("Creating node "+nodeName);
	                return true;
	            }
	            return true;
	        } catch (KeeperException e) {
	            System.out
	                    .println(" TestCreateZnode " +zk.toString()+" + Keeper exception when instantiating gla: "
	                            + e.toString());
	        } catch (InterruptedException e) {
	            System.out.println("Interrupted exception");
	        }
	        System.out.println("Exception raised");
	        return false;
    	}
    	System.out.println("zk connection doesn't exist");
    	return false;
    }
}

