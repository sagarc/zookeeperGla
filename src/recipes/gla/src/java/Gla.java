

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



public class Gla implements Watcher {

    static ZooKeeper zk = null;
    static Integer mutex;

    String root;

    Gla(String address) {
        if(zk == null){
            try {
                System.out.println("Starting ZK:");
                zk = new ZooKeeper(address, 3000, this);                
                System.out.println("Finished starting ZK: " + zk);
                root = "/gla";
            } catch (IOException e) {
                System.out.println(e.toString());
                zk = null;
            }
        }    
    }
    synchronized public void process(WatchedEvent event) {
        //Process events
    }

    private String DecodeValue(byte[] oldByteValue){    	
    	String oldValue = new String(oldByteValue);
    	return oldValue;    	
    }
    
    private String GetUnioun(String oldValue, String proposedValue){
    	String newValue;
    	if(((int)Integer.parseInt(oldValue)) < (int)(Integer.parseInt(proposedValue))) newValue = proposedValue;
    	else newValue = oldValue;
    	return newValue;
    }
    
    public String ReadValue(){
    	if (zk != null) {
    		Stat s = null;
			try {
				s = zk.exists(root, false);
			} catch (KeeperException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    		if(s==null) return "";
    		byte[] byteValue;
    		Stat oldStat = s;
    		try {
				byteValue = zk.getData(root,false, oldStat);
				String value = DecodeValue(byteValue);
				return value;
			} catch (KeeperException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		return "";    		
    	}
    	return "";
    }

    public void ProposeValueReplace(String proposedValue){    	
    	if (zk != null) {
	        try {	        	
	        	byte[] value = proposedValue.getBytes();
                
	            Stat s = zk.exists(root, false);
	            if (s == null) {
	            	//put the first proposedValue while creating znode itsle
	                zk.create(root, value, Ids.OPEN_ACL_UNSAFE,
	                        CreateMode.PERSISTENT);          
	            }
	            else{
	            	Stat oldStat = s; // assignment done for initializing stat
	            	Stat newStat= s;
	            	byte[] oldByteValue;
	            	String oldValue;
	            	String newValue;
	            	int oldVersion;
	            	int newVersion;
	            	int retryCount = 10;
	            	while(retryCount>0)
	            	{	            		
	            		oldByteValue = zk.getData(root,false, oldStat);
	            		oldVersion = oldStat.getVersion();
	            		
	            		oldValue = DecodeValue(oldByteValue);
	            		//System.out.println("oldval" + oldValue);
	            		//System.out.println("oldversion" + oldVersion);
	            		newValue = GetUnioun(oldValue,proposedValue);
	            		if(newValue == oldValue) break;
	            		value = newValue.getBytes();
	                		                	
	                	newStat = zk.setData(root,value,oldVersion);
	                	newVersion = newStat.getVersion();
	                	//System.out.println("New Version"+newVersion);
	                	if(newVersion == oldVersion+1) break;
	                	retryCount--;
	                	System.out.println("Retrying to set data");
		            }
	            	if(retryCount == 0 ){
	            		System.out.println("Value couldn't be written");
	            	}
            	}           
	        } catch (KeeperException e) {
	            System.out
	                    .println("Keeper exception when instantiating gla: "
	                            + e.toString());
	        } catch (InterruptedException e) {
	            System.out.println("Interrupted exception");
	        }  
	    }           
     }
    
    public void ProposeValueChild(int proposedValue){    	
    	if (zk != null) {
	        try {
	            Stat s = zk.exists(root, false);
	            if (s == null) {
	                zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE,
	                        CreateMode.PERSISTENT);
	                
	                
	                ByteBuffer b = ByteBuffer.allocate(4);
	                byte[] value;
	                b.clear();
	                b.putInt(proposedValue);
	                value = b.array();

	                //no watch since we need only current child         
	                List<String> list = zk.getChildren(root, false);
	                if(list.size()==0){
	                	zk.create(root + "/pValue", value, 
	                			Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
	                }
	                else{
	                	int maxInt = 1000000;
	                	for(String str: list){
	                		Stat stat;
	                		byte[] tempVal = zk.getData(str,false, s);
	                		int tempInt = ByteBuffer.wrap(tempVal).getInt();
	                		if(tempInt > maxInt) maxInt = tempInt;
	                	}
	                	b.clear();
	                	b.putInt(maxInt);
	                	value = b.array();
	                	zk.create(root + "/pValue", value, 
	                			Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
	                	for(String str: list){
	                		zk.delete(str, 0);
	                	}
	                }
	            }
	        } catch (KeeperException e) {
	            System.out
	                    .println("Keeper exception when instantiating gla: "
	                            + e.toString());
	        } catch (InterruptedException e) {
	            System.out.println("Interrupted exception");
	        }  
	    }           
     }
    
    public static boolean ParseOptions(String[] args) {
        List<String> argList = Arrays.asList(args);
        Iterator<String> it = argList.iterator();
        if(argList.size() == 0) return false;
        String cmd = it.next();        
        if(cmd.equalsIgnoreCase("readValue") && argList.size()==1){
        	return true;
        }
        else if(cmd.equalsIgnoreCase("proposeValue") && argList.size()==2){        	
        	return true;
        }
        else if(cmd.equalsIgnoreCase("quit")){
        	return true;
        }
        return false;
    }     	
        

    
    public static void main(String args[]){
    	System.out.println("Enter proposedValues, -1 to stop");
    	BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    	String proposedValue = "";
    	if(args.length != 1) {
    		System.out.println("Usage: address of server as parameter");
    		return;
    	}
    	Gla gla = new Gla(args[0]);
    	
    	String inputCmd;
    	String[] inputArgs;
    	while(true){
	    	try {
	    		inputCmd = "";
				inputCmd = br.readLine();				
				inputArgs = inputCmd.split("\\s");				
				if(!ParseOptions(inputArgs)){
					System.out.println("Usage:");
					System.out.println("	proposeValue val");
					System.out.println("	readValue");
					System.out.println("	quit");
					continue;
				}
				if(inputArgs[0].equalsIgnoreCase("readValue")){					
		    		String value = gla.ReadValue();
		    		System.out.println("Value is: " + value);
		    	}
		    	else if(inputArgs[0].equalsIgnoreCase("proposeValue")){
		    		gla.ProposeValueReplace(inputArgs[1]);
		    	}
		    	else if(inputArgs[0].equalsIgnoreCase("quit")){
		    		break;
		    	}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}    	
    	}    	
    }
}

