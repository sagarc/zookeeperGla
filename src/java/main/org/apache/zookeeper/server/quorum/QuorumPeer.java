/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.server.quorum;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;
import org.apache.zookeeper.jmx.MBeanRegistry;
import org.apache.zookeeper.jmx.ZKMBeanInfo;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.StateObject;
import org.apache.zookeeper.server.quorum.flexible.QuorumMaj;
import org.apache.zookeeper.server.quorum.flexible.QuorumVerifier;

/**
 * This class manages the quorum protocol. There are three states this server
 * can be in:
 * <ol>
 * <li>Leader election - each server will elect a leader (proposing itself as a
 * leader initially).</li>
 * <li>Follower - the server will synchronize with the leader and replicate any
 * transactions.</li>
 * <li>Leader - the server will process requests and forward them to followers.
 * A majority of followers must log the request before it can be accepted.
 * <li> Peer - the server acts as peer 
 * </ol>
 *
 * This class will setup a datagram socket that will always respond with its
 * view of the current leader. The response will take the form of:
 *
 * <pre>
 * int xid;
 *
 * long myid;
 *
 * long leader_id;
 *
 * long leader_zxid;
 * </pre>
 *
 * The request for the current leader will consist solely of an xid: int xid;
 */
public class QuorumPeer extends Thread implements QuorumStats.Provider {
    private static final Logger LOG = Logger.getLogger(QuorumPeer.class);

    QuorumBean jmxQuorumBean;
    LocalPeerBean jmxLocalPeerBean;
    LeaderElectionBean jmxLeaderElectionBean;
    public enum ServerState {
	    LOOKING, FOLLOWING, LEADING, OBSERVING, PEER;
	}

	QuorumCnxManager qcm;
	
	//no of servers initialy
	int initialNumberServers;
	
	//represents value of vector when it was last read
	//public int[] currentLearntValue;
    
    /* ZKDatabase is a top level member of quorumpeer 
     * which will be used in all the zookeeperservers
     * instantiated later. Also, it is created once on 
     * bootup and only thrown away in case of a truncate
     * message from the leader
     */
    private ZKDatabase zkDb;
    
    /**
     * Create an instance of a quorum peer
     */
    public interface Factory{
        public QuorumPeer create(NIOServerCnxn.Factory cnxnFactory) throws IOException;
        public NIOServerCnxn.Factory createConnectionFactory() throws IOException;
    }

    public static class QuorumServer {
        public QuorumServer(long id, InetSocketAddress addr,
                InetSocketAddress electionAddr) {
            this.id = id;
            this.addr = addr;
            this.electionAddr = electionAddr;            
        }

        public QuorumServer(long id, InetSocketAddress addr) {
            this.id = id;
            this.addr = addr;
            this.electionAddr = null;
        }
        
        public QuorumServer(long id, InetSocketAddress addr,
                    InetSocketAddress electionAddr, LearnerType type) {
            this.id = id;
            this.addr = addr;
            this.electionAddr = electionAddr;
            this.type = type;
        }
        
        public InetSocketAddress addr;

        public InetSocketAddress electionAddr;
        
        public long id;
        
        public LearnerType type = LearnerType.PARTICIPANT;
    }

    /*
     * A peer can either be participating, which implies that it is willing to
     * both vote in instances of consensus and to elect or become a Leader, or
     * it may be observing in which case it isn't.
     * 
     * We need this distinction to decide which ServerState to move to when 
     * conditions change (e.g. which state to become after LOOKING). 
     */
    public enum LearnerType {
        PARTICIPANT, OBSERVER;
    }
    
    /*
     * To enable observers to have no identifier, we need a generic identifier
     * at least for QuorumCnxManager. We use the following constant to as the
     * value of such a generic identifier. 
     */
    
    static final long OBSERVER_ID = Long.MAX_VALUE;
    
    /*
     * Default value of peer is participant
     */
    private LearnerType learnerType = LearnerType.PARTICIPANT;
    
    public LearnerType getLearnerType() {
        return learnerType;
    }
    
    /**
     * Sets the LearnerType both in the QuorumPeer and in the peerMap
     */
    public void setLearnerType(LearnerType p) {
        learnerType = p;
        if (quorumPeers.containsKey(this.myid)) {
            this.quorumPeers.get(myid).type = p;
        } else {
            LOG.error("Setting LearnerType to " + p + " but " + myid 
                    + " not in QuorumPeers. ");
        }
        
    }
    /**
     * The servers that make up the cluster
     */
    protected Map<Long, QuorumServer> quorumPeers;
    public int getQuorumSize(){
        return getVotingView().size();
    }
    
    /**
     * QuorumVerifier implementation; default (majority). 
     */
    
    private QuorumVerifier quorumConfig;
    
    /**
     * My id
     */
    private long myid;
    
    /*
     * Id of proposer. This id increases for every new proposal
     */
    private long proposerId = 0;
    
    public long getProposerId(){ return proposerId;}
    
    public void incrementProposerId(){
    	proposerId++;
    }
    
    public PeerCommunication pc;


    /**
     * get the id of this quorum peer.
     */
    public long getId() {
        return myid;
    }

    /**
     * This is who I think the leader currently is.
     */
    volatile private Vote currentVote;
        
    public synchronized Vote getCurrentVote(){
        return currentVote;
    }
       
    public synchronized void setCurrentVote(Vote v){
        currentVote = v;
    }    

    volatile boolean running = true;

    /**
     * The number of milliseconds of each tick
     */
    protected int tickTime;

    /**
     * Minimum number of milliseconds to allow for session timeout.
     * A value of -1 indicates unset, use default.
     */
    protected int minSessionTimeout = -1;

    /**
     * Maximum number of milliseconds to allow for session timeout.
     * A value of -1 indicates unset, use default.
     */
    protected int maxSessionTimeout = -1;

    /**
     * The number of ticks that the initial synchronization phase can take
     */
    protected int initLimit;

    /**
     * The number of ticks that can pass between sending a request and getting
     * an acknowledgment
     */
    protected int syncLimit;
    
    /*
     * This is used for whether we want distributed or central based protocol
     */
    protected boolean isLeaderNeeded;

    /**
     * The current tick
     */
    protected int tick;

    /**
     * This class simply responds to requests for the current leader of this
     * node.
     * <p>
     * The request contains just an xid generated by the requestor.
     * <p>
     * The response has the xid, the id of this server, the id of the leader,
     * and the zxid of the leader.
     *
     *
     */
    class ResponderThread extends Thread {
        ResponderThread() {
            super("ResponderThread");
        }

        volatile boolean running = true;
        
        @Override
        public void run() {
            try {
                byte b[] = new byte[36];
                ByteBuffer responseBuffer = ByteBuffer.wrap(b);
                DatagramPacket packet = new DatagramPacket(b, b.length);
                while (running) {
                    udpSocket.receive(packet);
                    if (packet.getLength() != 4) {
                        LOG.warn("Got more than just an xid! Len = "
                                + packet.getLength());
                    } else {
                        responseBuffer.clear();
                        responseBuffer.getInt(); // Skip the xid
                        responseBuffer.putLong(myid);
                        Vote current = getCurrentVote();
                        switch (getPeerState()) {
                        case LOOKING:
                        	LOG.info("Eventhread looking");
                            responseBuffer.putLong(current.id);
                            responseBuffer.putLong(current.zxid);
                            break;
                        case LEADING:
                        	LOG.info("Eventhread leading");
                            responseBuffer.putLong(myid);
                            try {
                                long proposed;
                                synchronized(leader) {
                                    proposed = leader.lastProposed;
                                }
                                responseBuffer.putLong(proposed);
                            } catch (NullPointerException npe) {
                                // This can happen in state transitions,
                                // just ignore the request
                            }
                            break;
                        case FOLLOWING:
                        	LOG.info("Eventhread following");
                            responseBuffer.putLong(current.id);
                            try {
                                responseBuffer.putLong(follower.getZxid());
                            } catch (NullPointerException npe) {
                                // This can happen in state transitions,
                                // just ignore the request
                            }
                            break;
                        case OBSERVING:
                            // Do nothing, Observers keep themselves to
                            // themselves. 
                            break;
                        case PEER:
                        	LOG.info("Eventhread peering");
                        	//MIght need to ping other servers
                        	break;
                        }
                        
                        	
                        packet.setData(b);
                        udpSocket.send(packet);
                    }
                    packet.setLength(b.length);
                }
            } catch (Exception e) {
                LOG.warn("Unexpected exception in ResponderThread",e);
            } finally {
                LOG.warn("QuorumPeer responder thread exited");
            }
        }
    }

    private ServerState state = ServerState.LOOKING;

    public synchronized void setPeerState(ServerState newState){
        state=newState;
    }

    public synchronized ServerState getPeerState(){
        return state;
    }

    DatagramSocket udpSocket;

    private InetSocketAddress myQuorumAddr;

    public InetSocketAddress getQuorumAddress(){
        return myQuorumAddr;
    }

    private int electionType;

    Election electionAlg;

    NIOServerCnxn.Factory cnxnFactory;
    private FileTxnSnapLog logFactory = null;

    private final QuorumStats quorumStats;
    
    public QuorumPeer() {
        super("QuorumPeer");
        quorumStats = new QuorumStats(this);
    }
    
   
    /**
     * For backward compatibility purposes, we instantiate QuorumMaj by default.
     */
    
    public QuorumPeer(Map<Long, QuorumServer> quorumPeers, File dataDir,
            File dataLogDir, int electionType,
            long myid, int tickTime, int initLimit, int syncLimit,
            NIOServerCnxn.Factory cnxnFactory) throws IOException {
        this(quorumPeers, dataDir, dataLogDir, electionType, myid, tickTime, 
        		initLimit, syncLimit, cnxnFactory, 
        		new QuorumMaj(countParticipants(quorumPeers)));
    }
    
    public QuorumPeer(Map<Long, QuorumServer> quorumPeers, File dataDir,
            File dataLogDir, int electionType,
            long myid, int tickTime, int initLimit, int syncLimit,
            NIOServerCnxn.Factory cnxnFactory, 
            QuorumVerifier quorumConfig) throws IOException {
        this();
        this.cnxnFactory = cnxnFactory;
        this.quorumPeers = quorumPeers;        
        this.electionType = electionType;
        this.myid = myid;
        this.tickTime = tickTime;
        this.initLimit = initLimit;
        this.syncLimit = syncLimit;        
        this.logFactory = new FileTxnSnapLog(dataLogDir, dataDir);
        this.zkDb = new ZKDatabase(this.logFactory);
        if(quorumConfig == null)
            this.quorumConfig = new QuorumMaj(countParticipants(quorumPeers));
        else this.quorumConfig = quorumConfig;
    }
    
    QuorumStats quorumStats() {
        return quorumStats;
    }
    
    @Override
    public synchronized void start() {
        try {
            zkDb.loadDataBase();           
        } catch(IOException ie) {
            LOG.fatal("Unable to load database on disk", ie);
            throw new RuntimeException("Unable to run quorum server ", ie);
        }
        cnxnFactory.start();        
        if(isLeaderNeeded){
        	LOG.info("Leader election started");
        	startLeaderElection();
        }else{        
        	startPeerConnections();
        }
        super.start();
    }
    
    void addProposals(byte[] proposalValue){    	
    	pc.addProposal(proposalValue, getProposerId());
    	incrementProposerId();
    }
    
    /*
     * this method with help of quorumCnxnManager should connect to all peers. the port used
     * For connection is electionaddress. 
     * Responder thread is not used here
     */    
    public void startPeerConnections(){
    	//initialize the currentLearntValue to latest value read from underlying GLA
    	
    	
    	//set the myQuorumAddr        
    	for (QuorumServer p : getView().values()) {
            if (p.id == myid) {
                myQuorumAddr = p.addr;
                break;
            }
        }
        if (myQuorumAddr == null) {
            throw new RuntimeException("My id " + myid + " not in the peer list");
        }
        
    	qcm = new QuorumCnxManager(this);    	
        QuorumCnxManager.Listener listener = qcm.listener;
        
        if(listener != null){
            listener.start();
            pc = new PeerCommunication(this, qcm);            
            //qcm.toSend(new Long(1), createMsg(ServerState.LOOKING.ordinal(), 0, 0, 1));
            /*
            int noServers = initialNumberServers;
            LOG.info("number of servers :" + noServers);
            int[] currentList = new int[noServers];
            for(int i=0;i<noServers;i++){
            	currentList[i] = ((int)myid-1+i)%3;
            }
            StateObject firstStateObj = new StateObject(noServers, currentList);
            //if(myid == 3){
            	firstStateObj.list[1] = 2; //proposed 1 2 1
            	byte[] proposalValue = firstStateObj.ObjToByte(firstStateObj);
            	//pc.addProposal(proposalValue, proposerId);
            	//qcm.toSend(new Long(1), createPeerMsg(0, 0, firstStateObj.ObjToByte(firstStateObj)));
            	//qcm.toSend(new Long(2), createPeerMsg(0, 0, firstStateObj.ObjToByte(firstStateObj)));
            	//qcm.toSend(new Long(3), createPeerMsg(0, 0, firstStateObj.ObjToByte(firstStateObj)));
            	firstStateObj.list[0] = 2;
            	firstStateObj.list[1] = 1; //proposed 2 1 1
            	proposalValue = firstStateObj.ObjToByte(firstStateObj);
            	addProposals(proposalValue);
            	*/
            //}      
        } else {
            LOG.error("Null listener when initializing cnx manager");
        }
    }
    
    ByteBuffer createMsg(int state, long leader, long zxid, long epoch){
        byte requestBytes[] = new byte[28];
        ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);  
        
        /*
         * Building notification packet to send
         */
                
        requestBuffer.clear();
        requestBuffer.putInt(state);
        requestBuffer.putLong(leader);
        requestBuffer.putLong(zxid);
        requestBuffer.putLong(epoch);
        
        return requestBuffer;
    }
    
    ByteBuffer createPeerMsg(int type, long proposalNumber, byte[] stateObjMessage){
        byte requestBytes[] = new byte[stateObjMessage.length + 20];
        ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);  
        
        /*
         * Building notification packet to send
         */
                
        requestBuffer.clear();
        requestBuffer.putInt(type);
        requestBuffer.putLong(proposalNumber);
        requestBuffer.put(stateObjMessage);  
        return requestBuffer;
    }

    ResponderThread responder;
    
    synchronized public void stopLeaderElection() {
        responder.running = false;
        responder.interrupt();
    }
    synchronized public void startLeaderElection() {
        currentVote = new Vote(myid, getLastLoggedZxid());
        for (QuorumServer p : getView().values()) {
            if (p.id == myid) {
                myQuorumAddr = p.addr;
                break;
            }
        }
        if (myQuorumAddr == null) {
            throw new RuntimeException("My id " + myid + " not in the peer list");
        }
        if (electionType == 0) {
            try {
                udpSocket = new DatagramSocket(myQuorumAddr.getPort());
                responder = new ResponderThread();
                responder.start();
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
        }
        this.electionAlg = createElectionAlgorithm(electionType);
    }
    
    /**
     * Count the number of nodes in the map that could be followers.
     * @param peers
     * @return The number of followers in the map
     */
    protected static int countParticipants(Map<Long,QuorumServer> peers) {
      int count = 0;
      for (QuorumServer q : peers.values()) {
          if (q.type == LearnerType.PARTICIPANT) {
              count++;
          }
      }
      return count;
    }
    
    /**
     * This constructor is only used by the existing unit test code.
     * It defaults to FileLogProvider persistence provider.
     */
    public QuorumPeer(Map<Long,QuorumServer> quorumPeers, File snapDir,
            File logDir, int clientPort, int electionAlg,
            long myid, int tickTime, int initLimit, int syncLimit)
        throws IOException
    {
        this(quorumPeers, snapDir, logDir, electionAlg,
                myid,tickTime, initLimit,syncLimit,
                new NIOServerCnxn.Factory(
                        new InetSocketAddress(clientPort)),
                new QuorumMaj(countParticipants(quorumPeers)));
    }
    
    /**
     * This constructor is only used by the existing unit test code.
     * It defaults to FileLogProvider persistence provider.
     */
    public QuorumPeer(Map<Long,QuorumServer> quorumPeers, File snapDir,
            File logDir, int clientPort, int electionAlg,
            long myid, int tickTime, int initLimit, int syncLimit, 
            QuorumVerifier quorumConfig)
        throws IOException
    {
        this(quorumPeers, snapDir, logDir, electionAlg,
                myid,tickTime, initLimit,syncLimit,
                new NIOServerCnxn.Factory(new InetSocketAddress(clientPort)),
                    quorumConfig);
    }
    
    /**
     * returns the highest zxid that this host has seen
     * 
     * @return the highest zxid for this host
     */
    public long getLastLoggedZxid() {
        long lastLogged= -1L;
        try {
            if (!zkDb.isInitialized()) {
                zkDb.loadDataBase();
            }
            lastLogged = zkDb.getDataTreeLastProcessedZxid();
        } catch(IOException ie) {
            LOG.warn("Unable to load database ", ie);
        }
        return lastLogged;
    }
    
    public Follower follower;
    public Leader leader;
    public Observer observer;
    public Peer peer;

    protected Follower makeFollower(FileTxnSnapLog logFactory) throws IOException {
        return new Follower(this, new FollowerZooKeeperServer(logFactory, 
                this,new ZooKeeperServer.BasicDataTreeBuilder(), this.zkDb));
    }
     
    protected Leader makeLeader(FileTxnSnapLog logFactory) throws IOException {
        return new Leader(this, new LeaderZooKeeperServer(logFactory,
                this,new ZooKeeperServer.BasicDataTreeBuilder(), this.zkDb));
    }
    
    protected Observer makeObserver(FileTxnSnapLog logFactory) throws IOException {
        return new Observer(this, new ObserverZooKeeperServer(logFactory,
                this, new ZooKeeperServer.BasicDataTreeBuilder(), this.zkDb));
    }
    
    protected Peer makePeer(FileTxnSnapLog logFactory) throws IOException {
    	return new Peer(this, new PeerZooKeeperServer(logFactory, this, 
    			new ZooKeeperServer.BasicDataTreeBuilder(), this.zkDb));
    }

    protected Election createElectionAlgorithm(int electionAlgorithm){
        Election le=null;
                
        //TODO: use a factory rather than a switch
        switch (electionAlgorithm) {
        case 0:
            le = new LeaderElection(this);
            break;
        case 1:
            le = new AuthFastLeaderElection(this);
            break;
        case 2:
            le = new AuthFastLeaderElection(this, true);
            break;
        case 3:
            qcm = new QuorumCnxManager(this);
            QuorumCnxManager.Listener listener = qcm.listener;
            if(listener != null){
                listener.start();
                le = new FastLeaderElection(this, qcm);
            } else {
                LOG.error("Null listener when initializing cnx manager");
            }
            break;
        default:
            assert false;
        }
        return le;
    }

    protected Election makeLEStrategy(){
        LOG.debug("Initializing leader election protocol...");
        if (getElectionType() == 0) {
            electionAlg = new LeaderElection(this);
        }        
        return electionAlg;
    }

    synchronized protected void setPeer(Peer newPeer){
    	peer = newPeer;
    }
    
    synchronized protected void setLeader(Leader newLeader){
        leader=newLeader;
    }

    synchronized protected void setFollower(Follower newFollower){
        follower=newFollower;
    }
    
    synchronized protected void setObserver(Observer newObserver){
        observer=newObserver;
    }

    synchronized public ZooKeeperServer getActiveServer(){
        if(leader!=null)
            return leader.zk;
        else if(follower!=null)
            return follower.zk;
        else if (observer != null)
            return observer.zk;
        return null;
    }
    
    public int processCmd(String cmd) throws InterruptedException{    	
    	String delims = "[ ]+";
    	String[] tokens = cmd.split(delims);
    	if(tokens.length != 1 && tokens.length !=2){
    		System.out.println("Invalid arguemnts "+ tokens[0] + " "+tokens[1]);    		
    	}
    	else if(tokens[0].equals("propose")){
    		int propVal = Integer.parseInt(tokens[1]);
    		int[] currLearntVal = StateObject.ByteToObj(pc.learntValue).list;
    		if(propVal > currLearntVal[(int)myid-1]){
    			currLearntVal[(int)myid -1 ] = propVal;
    		}
    		StateObject newObj = new StateObject(initialNumberServers, currLearntVal);
    		addProposals(StateObject.ObjToByte(newObj));
    		return 1;
    	}
    	else if(tokens[0].equals("read")){
    		System.out.println("Current value is: " + StateObject.printByteValue(pc.learntValue)+"\n");
    		return 1;
    	}
    	else if(tokens[0].equals("readlatest")){
    		int[] currLearntVal = StateObject.ByteToObj(pc.learntValue).list;    		
    		currLearntVal[(int)myid -1 ] = 0; // no-op operation issued to get latest value    		
    		StateObject newObj = new StateObject(initialNumberServers, currLearntVal);
    		addProposals(StateObject.ObjToByte(newObj));
    		Thread.sleep(1000);
    		System.out.println("Current value is: " + StateObject.printByteValue(pc.learntValue)+"\n");
    		return 1;
    		
    	}
    	else if (tokens[0].equals("quit")){
    		return 0;
    	}
    	System.out.println("Re Try with valid command");
    	return 1;
    }

    @Override
    public void run() {
        setName("QuorumPeer:" + cnxnFactory.getLocalAddress());

        LOG.debug("Starting quorum peer");
        try {
            jmxQuorumBean = new QuorumBean(this);
            MBeanRegistry.getInstance().register(jmxQuorumBean, null);
            for(QuorumServer s: getView().values()){
                ZKMBeanInfo p;
                if (getId() == s.id) {
                    p = jmxLocalPeerBean = new LocalPeerBean(this);
                    try {
                        MBeanRegistry.getInstance().register(p, jmxQuorumBean);
                    } catch (Exception e) {
                        LOG.warn("Failed to register with JMX", e);
                        jmxLocalPeerBean = null;
                    }
                } else {
                    p = new RemotePeerBean(s);
                    try {
                        MBeanRegistry.getInstance().register(p, jmxQuorumBean);
                    } catch (Exception e) {
                        LOG.warn("Failed to register with JMX", e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to register with JMX", e);
            jmxQuorumBean = null;
        }

        try {
            /*
             * Main loop
             */
            while (running) {
                switch (getPeerState()) {
                case LOOKING:
                    try {
                        //LOG.info("LOOKING");
                        if(isLeaderNeeded){
                        	setCurrentVote(makeLEStrategy().lookForLeader());
                        }else{
                        	LOG.info("no leader election required");
                        }
                    } catch (Exception e) {
                        LOG.warn("Unexpected exception",e);
                        setPeerState(ServerState.LOOKING);
                    }
                    break;
                case OBSERVING:
                    try {
                        LOG.info("OBSERVING");
                        setObserver(makeObserver(logFactory));
                        observer.observeLeader();
                    } catch (Exception e) {
                        LOG.warn("Unexpected exception",e );                        
                    } finally {
                        observer.shutdown();
                        setObserver(null);
                        setPeerState(ServerState.LOOKING);
                    }
                    break;
                case FOLLOWING:
                    try {
                        LOG.info("FOLLOWING");
                        setFollower(makeFollower(logFactory));
                        follower.followLeader();
                    } catch (Exception e) {
                        LOG.warn("Unexpected exception",e);
                    } finally {
                        follower.shutdown();
                        setFollower(null);
                        setPeerState(ServerState.LOOKING);
                    }
                    break;
                case LEADING:
                    LOG.info("LEADING");
                    try {
                        setLeader(makeLeader(logFactory));
                        leader.lead();
                        setLeader(null);
                    } catch (Exception e) {
                        LOG.warn("Unexpected exception",e);
                    } finally {
                        if (leader != null) {
                            leader.shutdown("Forcing shutdown");
                            setLeader(null);
                        }
                        setPeerState(ServerState.LOOKING);
                    }
                    break;                
                case PEER:                
                	try{
                		if(logFactory!= null){
                			setPeer(makePeer(logFactory));
                			//LOG.info("Peer is set");
                			//setLeader(makeLeader(logFactory));
                            //leader.lead();
                            //setLeader(null);
                			BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
                    	    String inputCommand = bufferRead.readLine();
                			while(processCmd(inputCommand)==1){      
                				//LOG.info("Received input as proposal");
                				bufferRead = new BufferedReader(new InputStreamReader(System.in));
                        	    inputCommand = bufferRead.readLine();	                				                			
                			}
                			Thread.sleep(1000);
                		}
                		else {
                			LOG.error("logFactory is not set");
                			System.exit(3);
                		}
                	} catch(Exception e){
                		LOG.warn("unexpected exception",e);
                	}finally{
                		//peer.shutdown("Shutdown");
                        setPeer(null);
                        setPeerState(ServerState.PEER);
                        LOG.info("Closing peer");
                        /*if (leader != null) {
                            leader.shutdown("Forcing shutdown");
                            setLeader(null);
                        }*/
                        //setPeerState(ServerState.PEER);                        
                	}
                	break;
                }
            }
        } finally {
            LOG.warn("QuorumPeer main thread exited");
            try {
                MBeanRegistry.getInstance().unregisterAll();
            } catch (Exception e) {
                LOG.warn("Failed to unregister with JMX", e);
            }
            jmxQuorumBean = null;
            jmxLocalPeerBean = null;
        }
    }

    public void shutdown() {
        running = false;
        if (leader != null) {
            leader.shutdown("quorum Peer shutdown");
        }
        if (follower != null) {
            follower.shutdown();
        }
        if(peer!=null){
        	peer.shutdown("shutdown by quorumpeer");
        }
        cnxnFactory.shutdown();
        if(udpSocket != null) {
            udpSocket.close();
        }
        
        if(getElectionAlg() != null){
        	getElectionAlg().shutdown();
        }
        try {
            zkDb.close();
        } catch (IOException ie) {
            LOG.warn("Error closing logs ", ie);
        }     
    }

    /**
     * A 'view' is a node's current opinion of the membership of the entire
     * ensemble.    
     */
    public Map<Long,QuorumPeer.QuorumServer> getView() {
        return Collections.unmodifiableMap(this.quorumPeers);
    }
    
    /**
     * Observers are not contained in this view, only nodes with 
     * PeerType=PARTICIPANT.     
     */
    public Map<Long,QuorumPeer.QuorumServer> getVotingView() {
        Map<Long,QuorumPeer.QuorumServer> ret = 
            new HashMap<Long, QuorumPeer.QuorumServer>();
        Map<Long,QuorumPeer.QuorumServer> view = getView();
        for (QuorumServer server : view.values()) {            
            if (server.type == LearnerType.PARTICIPANT) {
                ret.put(server.id, server);
            }
        }        
        return ret;
    }
    
    /**
     * Returns only observers, no followers.
     */
    public Map<Long,QuorumPeer.QuorumServer> getObservingView() {
        Map<Long,QuorumPeer.QuorumServer> ret = 
            new HashMap<Long, QuorumPeer.QuorumServer>();
        Map<Long,QuorumPeer.QuorumServer> view = getView();
        for (QuorumServer server : view.values()) {            
            if (server.type == LearnerType.OBSERVER) {
                ret.put(server.id, server);
            }
        }        
        return ret;
    }
    
    /**
     * Check if a node is in the current view. With static membership, the
     * result of this check will never change; only when dynamic membership
     * is introduced will this be more useful.
     */
    public boolean viewContains(Long sid) {
        return this.quorumPeers.containsKey(sid);
    }
    
    /**
     * Only used by QuorumStats at the moment
     */
    public String[] getQuorumPeers() {
        List<String> l = new ArrayList<String>();
        synchronized (this) {
            if (leader != null) {
                synchronized (leader.learners) {
                    for (LearnerHandler fh : leader.learners) {
                        if (fh.getSocket() == null)
                            continue;
                        String s = fh.getSocket().getRemoteSocketAddress().toString();
                        if (leader.isLearnerSynced(fh))
                            s += "*";
                        l.add(s);
                    }
                }
            } else if (follower != null) {
                l.add(follower.sock.getRemoteSocketAddress().toString());
            }
        }
        return l.toArray(new String[0]);
    }

    public String getServerState() {
        switch (getPeerState()) {
        case LOOKING:
            return QuorumStats.Provider.LOOKING_STATE;
        case LEADING:
            return QuorumStats.Provider.LEADING_STATE;
        case FOLLOWING:
            return QuorumStats.Provider.FOLLOWING_STATE;
        case OBSERVING:
            return QuorumStats.Provider.OBSERVING_STATE;
        case PEER:
        	return QuorumStats.Provider.PEER_STATE;
        }
        return QuorumStats.Provider.UNKNOWN_STATE;
    }


    /**
     * get the id of this quorum peer.
     */
    public long getMyid() {
        return myid;
    }

    /**
     * set the id of this quorum peer.
     */
    public void setMyid(long myid) {
        this.myid = myid;
    }

    /**
     * Get the number of milliseconds of each tick
     */
    public int getTickTime() {
        return tickTime;
    }

    /**
     * Set the number of milliseconds of each tick
     */
    public void setTickTime(int tickTime) {
        LOG.info("tickTime set to " + tickTime);
        this.tickTime = tickTime;
    }

    /** minimum session timeout in milliseconds */
    public int getMinSessionTimeout() {
        return minSessionTimeout == -1 ? tickTime * 2 : minSessionTimeout;
    }

    /** minimum session timeout in milliseconds */
    public void setMinSessionTimeout(int min) {
        LOG.info("minSessionTimeout set to " + min);
        this.minSessionTimeout = min;
    }

    /** maximum session timeout in milliseconds */
    public int getMaxSessionTimeout() {
        return maxSessionTimeout == -1 ? tickTime * 20 : maxSessionTimeout;
    }

    /** minimum session timeout in milliseconds */
    public void setMaxSessionTimeout(int max) {
        LOG.info("maxSessionTimeout set to " + max);
        this.maxSessionTimeout = max;
    }

    /**
     * Get the number of ticks that the initial synchronization phase can take
     */
    public int getInitLimit() {
        return initLimit;
    }

    /**
     * Set the number of ticks that the initial synchronization phase can take
     */
    public void setInitLimit(int initLimit) {
        LOG.info("initLimit set to " + initLimit);
        this.initLimit = initLimit;
    }

    /**
     * Get the current tick
     */
    public int getTick() {
        return tick;
    }
    
    /**
     * Return QuorumVerifier object
     */
    
    public QuorumVerifier getQuorumVerifier(){
        return quorumConfig;
        
    }
    
    public void setQuorumVerifier(QuorumVerifier quorumConfig){
       this.quorumConfig = quorumConfig;
    }
    
    /**
     * Get an instance of LeaderElection
     */
        
    public Election getElectionAlg(){
        return electionAlg;
    }
        
    /**
     * Get the synclimit
     */
    public int getSyncLimit() {
        return syncLimit;
    }

    /**
     * Set the synclimit
     */
    public void setSyncLimit(int syncLimit) {
        this.syncLimit = syncLimit;
    }
    
    public void setIsLeaderNeeded(boolean need){
    	this.isLeaderNeeded = need;
    }
    
    /**
     * Gets the election type
     */
    public int getElectionType() {
        return electionType;
    }

    /**
     * Sets the election type
     */
    public void setElectionType(int electionType) {
        this.electionType = electionType;
    }

    public NIOServerCnxn.Factory getCnxnFactory() {
        return cnxnFactory;
    }

    public void setCnxnFactory(NIOServerCnxn.Factory cnxnFactory) {
        this.cnxnFactory = cnxnFactory;
    }

    public void setQuorumPeers(Map<Long,QuorumServer> quorumPeers) {
        this.quorumPeers = quorumPeers;
    }

    public int getClientPort() {
        return cnxnFactory.getLocalPort();
    }

    public void setClientPortAddress(InetSocketAddress addr) {
    }
 
    public void setTxnFactory(FileTxnSnapLog factory) {
        this.logFactory = factory;
        LOG.info("setting up transaction log factory");
    }
    
    public FileTxnSnapLog getTxnFactory() {
        return this.logFactory;
    }

    /**
     * set zk database for this node
     * @param database
     */
    public void setZKDatabase(ZKDatabase database) {
        this.zkDb = database;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * get reference to QuorumCnxManager
     */
    public QuorumCnxManager getQuorumCnxManager() {
        return qcm;
    }
    
    static private class MyCommandOptions {

        private Map<String,String> options = new HashMap<String,String>();
        private List<String> cmdArgs = null;
        private String command = null;

        public MyCommandOptions() {
          //options.put("server", "localhost:2181");
          //options.put("timeout", "30000");
        }

        public String getOption(String opt) {
            return options.get(opt);
        }

        public String getCommand( ) {
            return command;
        }

        public String getCmdArgument( int index ) {
            return cmdArgs.get(index);
        }

        public int getNumArguments( ) {
            return cmdArgs.size();
        }

        public String[] getArgArray() {
            return cmdArgs.toArray(new String[0]);
        }

        /**
         * Parses a command line that may contain one or more flags
         * before an optional command string
         * @param args command line arguments
         * @return true if parsing succeeded, false otherwise.
         */
        public boolean parseOptions(String[] args) {
            List<String> argList = Arrays.asList(args);
            Iterator<String> it = argList.iterator();

            while (it.hasNext()) {
                String opt = it.next();
                try {
                    if (opt.equals("-server")) {
                        options.put("server", it.next());
                    } else if (opt.equals("-timeout")) {
                        options.put("timeout", it.next());
                    }
                } catch (NoSuchElementException e){
                    System.err.println("Error: no argument found for option "
                            + opt);
                    return false;
                }

                if (!opt.startsWith("-")) {
                    command = opt;
                    cmdArgs = new ArrayList<String>( );
                    cmdArgs.add( command );
                    while (it.hasNext()) {
                        cmdArgs.add(it.next());
                    }
                    return true;
                }
            }
            return true;
        }

        /**
         * Breaks a string into command + arguments.
         * @param cmdstring string of form "cmd arg1 arg2..etc"
         * @return true if parsing succeeded.
         */
        public boolean parseCommand( String cmdstring ) {
            String[] args = cmdstring.split(" ");
            if (args.length == 0){
                return false;
            }
            command = args[0];
            cmdArgs = Arrays.asList(args);
            return true;
        }
    }


    
    
    
}
