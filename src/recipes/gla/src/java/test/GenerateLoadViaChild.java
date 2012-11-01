package test;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

import zkGla.ILatticeValue;
import zkGla.StateVector;
import zkGla.ILatticeValue.Version;
import zkGla.StateVectorViaChild;

public class GenerateLoadViaChild {
	
	

	public int totalProposers;
	public String address;	
	static class theLock extends Object {
	   }
	static public theLock lockObject = new theLock();
	
	class GlaThread extends Thread{
		public int totalOperations;
		public int updatePercent;
		public int myId;
		public StateVectorViaChild stateVecChild;
		GlaThread(){}
		GlaThread(int to, int up, int id, String add, int totProp, String name){
			super(name);
			totalOperations = to;
			updatePercent = up;
			myId = id;
			//System.out.println(add + " "+id+" "+name);
			stateVecChild = new StateVectorViaChild(add, totProp, id);
			start();
		}
		
		public void run(){		
			String threadName =getName(); 
			System.out.println("Thread name "+threadName);
			Random rand = new Random();
			Random rand1 = new Random();
			FileWriter fstream=null;
			try {
				fstream = new FileWriter("analysis");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			BufferedWriter out = new BufferedWriter(fstream);
			
			long startTime = System.currentTimeMillis();
			for(int i=0;i<totalOperations;i++){
				 				 
				 int decide  = rand.nextInt(100);
				 if(decide > updatePercent){					 
			    	 byte[] value = stateVecChild.ReadValueViaChild();
			    	 /*if(value!=null)
						try {
							//synchronized(lockObject){
								out.write(stateVecChild.PrintValue(value)+"\n");
							//}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}*/
				 }
				 else{
					 //int updateValue = rand1.nextInt(10*totalOperations);
					 int updateValue  = i;
					 byte[] proposeValue = stateVecChild.GetProposedValue(((Integer)updateValue).toString());
			    	 if(proposeValue!=null)stateVecChild.ProposeValueViaChild(proposeValue);					 
				}
			}
			long endTime = System.currentTimeMillis();
			long diffTime = endTime - startTime;
			System.out.println("Time for " + threadName +" is: "+ diffTime);
			try {
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	//public GlaThread[] proposerThread;
	
	public static void main(String args[]){    	
		
		if(args.length != 3) {
    		System.out.println("Usage: addressServer, totalNumProposers, configFile");
    		return;
    	}		
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(args[2]));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			System.out.println("config file not found");
			e1.printStackTrace();
			return;
		}   	
    	GenerateLoadViaChild gLoad = new GenerateLoadViaChild();
    	int totalProposers = Integer.parseInt(args[1]);
    	String address = args[0];
    	    	
    	for(int i=0;i<totalProposers;i++){
    		String param="";
			try {
				//System.out.println("Enter: totalOperation, updatePercent"); 
				param = br.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		String[] parameters = param.split("\\s");
    		int totalOperations = Integer.parseInt(parameters[0]);
    		int updatePercent = Integer.parseInt(parameters[1]);
    		gLoad.new GlaThread(totalOperations,
    				updatePercent, i, address, totalProposers,"Thread"+i );
    		try{
    			Thread.sleep(1000);    		
    		}catch(Exception e){
    			System.out.println("Thread Sleeping");
    		}
    	}
	}	
}
