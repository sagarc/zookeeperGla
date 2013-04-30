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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import org.apache.jute.BinaryInputArchive;
import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.Record;
import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException.SessionExpiredException;
import org.apache.zookeeper.ZooDefs.OpCode;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.ZooTrace;
//import org.apache.zookeeper.server.quorum.Peer.Proposal;
import org.apache.zookeeper.server.quorum.Leader.Proposal;
import org.apache.zookeeper.server.quorum.QuorumPeer.LearnerType;
import org.apache.zookeeper.server.util.SerializeUtils;
import org.apache.zookeeper.txn.TxnHeader;

/**
 * There will be an instance of this class created by the Leader for each
 * learner. All communication with a learner is handled by this
 * class.
 */
public class PeerLearnerHandler extends Thread {
    private static final Logger LOG = Logger.getLogger(LearnerHandler.class);

    protected final Socket sock;    

    public Socket getSocket() {
        return sock;
    }

    final Peer peer;

    long tickOfLastAck;
    
    /**
     * ZooKeeper server identifier of this learner
     */
    protected long sid = 0;
    
    long getSid(){
        return sid;
    }                    

    /**
     * The packets to be sent to the learner
     */
    final LinkedBlockingQueue<QuorumPacket> queuedPackets =
        new LinkedBlockingQueue<QuorumPacket>();

    private BinaryInputArchive ia;

    private BinaryOutputArchive oa;

    private BufferedOutputStream bufferedOutput;

    PeerLearnerHandler(Socket sock, Peer peer) throws IOException {
        super("PeerLearnerHandler-" + sock.getRemoteSocketAddress());        
        this.sock = sock;
        this.peer = peer;
        peer.addLearnerHandler(this);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LearnerHandler ").append(sock);
        sb.append(" tickOfLastAck:").append(tickOfLastAck());
        sb.append(" synced?:").append(synced());
        sb.append(" queuedPacketLength:").append(queuedPackets.size());
        return sb.toString();
    }

    /**
     * If this packet is queued, the sender thread will exit
     */
    final QuorumPacket proposalOfDeath = new QuorumPacket();

    private LearnerType  learnerType = LearnerType.PARTICIPANT;
    public LearnerType getLearnerType() {
        return learnerType;
    }

    /**
     * This method will use the thread to send packets added to the
     * queuedPackets list
     *
     * @throws InterruptedException
     */
    private void sendPackets() throws InterruptedException {
        long traceMask = ZooTrace.SERVER_PACKET_TRACE_MASK;
        while (true) {
            try {
                QuorumPacket p;
                p = queuedPackets.poll();
                if (p == null) {
                    bufferedOutput.flush();
                    p = queuedPackets.take();
                }

                if (p == proposalOfDeath) {
                    // Packet of death!
                    break;
                }
                if (p.getType() == Peer.PING) {
                    traceMask = ZooTrace.SERVER_PING_TRACE_MASK;
                }
                if (LOG.isTraceEnabled()) {
                    ZooTrace.logQuorumPacket(LOG, traceMask, 'o', p);
                }
                oa.writeRecord(p, "packet");
            } catch (IOException e) {
                if (!sock.isClosed()) {
                    LOG.warn("Unexpected exception at " + this, e);
                    try {
                        // this will cause everything to shutdown on
                        // this learner handler and will help notify
                        // the learner/observer instantaneously
                        sock.close();
                    } catch(IOException ie) {
                        LOG.warn("Error closing socket for handler " + this, ie);
                    }
                }
                break;
            }
        }
    }

    static public String packetToString(QuorumPacket p) {
        if (true)
            return null;
        String type = null;
        String mess = null;
        Record txn = null;
        
        switch (p.getType()) {
        case Peer.ACK:
            type = "ACK";
            break;
        case Peer.COMMIT:
            type = "COMMIT";
            break;
        case Peer.FOLLOWERINFO:
            type = "FOLLOWERINFO";
            break;    
       
        case Peer.PING:
            type = "PING";
            break;
        case Peer.PROPOSAL:
            type = "PROPOSAL";
            BinaryInputArchive ia = BinaryInputArchive
                    .getArchive(new ByteArrayInputStream(p.getData()));
            TxnHeader hdr = new TxnHeader();
            try {
                txn = SerializeUtils.deserializeTxn(ia, hdr);
                // mess = "transaction: " + txn.toString();
            } catch (IOException e) {
                LOG.warn("Unexpected exception",e);
            }
            break;
        case Peer.REQUEST:
            type = "REQUEST";
            break;
        case Peer.REVALIDATE:
            type = "REVALIDATE";
            ByteArrayInputStream bis = new ByteArrayInputStream(p.getData());
            DataInputStream dis = new DataInputStream(bis);
            try {
                long id = dis.readLong();
                mess = " sessionid = " + id;
            } catch (IOException e) {
                LOG.warn("Unexpected exception", e);
            }

            break;
        case Peer.UPTODATE:
            type = "UPTODATE";
            break;
        default:
            type = "UNKNOWN" + p.getType();
        }
        String entry = null;
        if (type != null) {
            entry = type + " " + Long.toHexString(p.getZxid()) + " " + mess;
        }
        return entry;
    }

    /**
     * This thread will receive packets from the peer and process them and
     * also listen to new connections from new peers.
     */
    @Override
    public void run() {
        try {
            
            ia = BinaryInputArchive.getArchive(new BufferedInputStream(sock
                    .getInputStream()));
            bufferedOutput = new BufferedOutputStream(sock.getOutputStream());
            oa = BinaryOutputArchive.getArchive(bufferedOutput);

            QuorumPacket qp = new QuorumPacket();
            ia.readRecord(qp, "packet");
            if(qp.getType() != Peer.FOLLOWERINFO && qp.getType() != Peer.OBSERVERINFO){
            	LOG.error("First packet " + qp.toString()
                        + " is not FOLLOWERINFO or OBSERVERINFO!");
                return;
            }
            if (qp.getData() != null) {
            	ByteBuffer bbsid = ByteBuffer.wrap(qp.getData());
                this.sid = bbsid.getLong();
            } else {
            	this.sid = Peer.followerCounter.getAndDecrement();
            }

           // LOG.info("Follower sid: " + this.sid + " : info : "
             //       + Peer.self.quorumPeers.get(this.sid));
                        
            if (qp.getType() == Peer.OBSERVERINFO) {
                  learnerType = LearnerType.OBSERVER;
            }            
            
            long peerLastZxid = qp.getZxid();
            /* the default to send to the follower */
            int packetToSend = Peer.SNAP;
            long zxidToSend = 0;
            long PeerLastZxid = 0;
            /** the packets that the follower needs to get updates from **/
            long updates = peerLastZxid;
            
            /* we are sending the diff check if we have proposals in memory to be able to 
             * send a diff to the 
             */ 
            ReentrantReadWriteLock lock = peer.zk.getZKDatabase().getLogLock();
            ReadLock rl = lock.readLock();
            try {
                rl.lock();        
                final long maxCommittedLog = peer.zk.getZKDatabase().getmaxCommittedLog();
                final long minCommittedLog = peer.zk.getZKDatabase().getminCommittedLog();
                LOG.info("Synchronizing with Follower sid: " + this.sid
                        +" maxCommittedLog ="+Long.toHexString(maxCommittedLog)
                        +" minCommittedLog = "+Long.toHexString(minCommittedLog)
                        +" peerLastZxid = "+Long.toHexString(peerLastZxid));

                LinkedList<Proposal> proposals = peer.zk.getZKDatabase().getCommittedLog();

                if (proposals.size() != 0) {
                    if ((maxCommittedLog >= peerLastZxid)
                            && (minCommittedLog <= peerLastZxid)) {

                        // as we look through proposals, this variable keeps track of previous
                        // proposal Id.
                        long prevProposalZxid = minCommittedLog;

                        // Keep track of whether we are about to send the first packet.
                        // Before sending the first packet, we have to tell the learner
                        // whether to expect a trunc or a diff
                        boolean firstPacket=true;

                        for (Proposal propose: proposals) {
                            // skip the proposals the peer already has
                            if (propose.packet.getZxid() <= peerLastZxid) {
                                prevProposalZxid = propose.packet.getZxid();
                                continue;
                            } else {
                                // If we are sending the first packet, figure out whether to trunc
                                // in case the follower has some proposals that the peer doesn't
                                if (firstPacket) {
                                    firstPacket = false;
                                    // Does the peer have some proposals that the peer hasn't seen yet
                                    if (prevProposalZxid < peerLastZxid) {
                                        // send a trunc message before sending the diff
                                        packetToSend = Peer.TRUNC;
                                        LOG.info("Sending TRUNC");
                                        zxidToSend = prevProposalZxid;
                                        updates = zxidToSend;
                                    } 
                                    else {
                                        // Just send the diff
                                        packetToSend = Peer.DIFF;
                                        LOG.info("Sending diff");
                                        zxidToSend = maxCommittedLog;        
                                    }

                                }
                                queuePacket(propose.packet);
                                QuorumPacket qcommit = new QuorumPacket(Peer.COMMIT, propose.packet.getZxid(),
                                        null, null);
                                queuePacket(qcommit);
                            }
                        }
                    } else if (peerLastZxid > maxCommittedLog) {
                        packetToSend = Peer.TRUNC;
                        zxidToSend = maxCommittedLog;
                        updates = zxidToSend;
                    }
                } else {
                    // just let the state transfer happen
                }               

                peerLastZxid = peer.startForwarding(this, updates);
                if (peerLastZxid == peerLastZxid) {
                    // We are in sync so we'll do an empty diff
                    packetToSend = Peer.DIFF;
                    zxidToSend = peerLastZxid;
                }
            } finally {
                rl.unlock();
            }

            QuorumPacket newPeerQP = new QuorumPacket(Peer.NEWLEADER,
                    peerLastZxid, null, null);
            oa.writeRecord(newPeerQP, "packet");
            bufferedOutput.flush();
            //Need to set the zxidToSend to the latest zxid
            if (packetToSend == Peer.SNAP) {
                zxidToSend = peer.zk.getZKDatabase().getDataTreeLastProcessedZxid();
            }
            oa.writeRecord(new QuorumPacket(packetToSend, zxidToSend, null, null), "packet");
            bufferedOutput.flush();
            
            /* if we are not truncating or sending a diff just send a snapshot */
            if (packetToSend == Peer.SNAP) {
                LOG.info("Sending snapshot last zxid of peer is 0x"
                        + Long.toHexString(peerLastZxid) + " " 
                        + " zxid of peer is 0x"
                        + Long.toHexString(peerLastZxid)
                        + "sent zxid of db as 0x" 
                        + Long.toHexString(zxidToSend));
                // Dump data to peer
                peer.zk.getZKDatabase().serializeSnapshot(oa);
                oa.writeString("BenWasHere", "signature");
            }
            bufferedOutput.flush();
            
            // Mutation packets will be queued during the serialize,
            // so we need to mark when the peer can actually start
            // using the data
            //
            queuedPackets
                    .add(new QuorumPacket(Peer.UPTODATE, -1, null, null));

            // Start sending packets
            new Thread() {
                public void run() {
                    Thread.currentThread().setName(
                            "Sender-" + sock.getRemoteSocketAddress());
                    try {
                        sendPackets();
                    } catch (InterruptedException e) {
                        LOG.warn("Unexpected interruption",e);
                    }
                }
            }.start();
            
            /*
             * Have to wait for the first ACK, wait until 
             * the peer is ready, and only then we can
             * start processing messages.
             */
            qp = new QuorumPacket();
            ia.readRecord(qp, "packet");
            if(qp.getType() != Peer.ACK){
                LOG.error("Next packet was supposed to be an ACK");
                return;
            }
            peer.processAck(this.sid, qp.getZxid(), sock.getLocalSocketAddress());
            
            /*
             * Wait until peer starts up
             */
            synchronized(peer.zk){
                while(!peer.zk.isRunning()){
                    peer.zk.wait(500);
                }
            }
            
            while (true) {
                qp = new QuorumPacket();
                ia.readRecord(qp, "packet");

                long traceMask = ZooTrace.SERVER_PACKET_TRACE_MASK;
                if (qp.getType() == Peer.PING) {
                    traceMask = ZooTrace.SERVER_PING_TRACE_MASK;
                }
                if (LOG.isTraceEnabled()) {
                    ZooTrace.logQuorumPacket(LOG, traceMask, 'i', qp);
                }
                tickOfLastAck = peer.self.tick;


                ByteBuffer bb;
                long sessionId;
                int cxid;
                int type;

                switch (qp.getType()) {
                case Peer.ACK:
                    if (this.learnerType == LearnerType.OBSERVER) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Received ACK from Observer  " + this.sid);
                        }
                    }
                    peer.processAck(this.sid, qp.getZxid(), sock.getLocalSocketAddress());
                    break;
                case Peer.PING:
                    // Process the touches
                    ByteArrayInputStream bis = new ByteArrayInputStream(qp
                            .getData());
                    DataInputStream dis = new DataInputStream(bis);
                    while (dis.available() > 0) {
                        long sess = dis.readLong();
                        int to = dis.readInt();
                        peer.zk.touch(sess, to);
                    }
                    break;
                case Peer.REVALIDATE:
                    bis = new ByteArrayInputStream(qp.getData());
                    dis = new DataInputStream(bis);
                    long id = dis.readLong();
                    int to = dis.readInt();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeLong(id);
                    boolean valid = peer.zk.touch(id, to);
                    if (valid) {
                        try {
                            //set the session owner
                            // as the follower that
                            // owns the session
                            peer.zk.setOwner(id, this);
                        } catch (SessionExpiredException e) {
                            LOG.error("Somehow session " + Long.toHexString(id) + " expired right after being renewed! (impossible)", e);
                        }
                    }
                    if (LOG.isTraceEnabled()) {
                        ZooTrace.logTraceMessage(LOG,
                                                 ZooTrace.SESSION_TRACE_MASK,
                                                 "Session 0x" + Long.toHexString(id)
                                                 + " is valid: "+ valid);
                    }
                    dos.writeBoolean(valid);
                    qp.setData(bos.toByteArray());
                    queuedPackets.add(qp);
                    break;
                //in orginal code request is sent from observer to leader. But here we dont want to send these packets
                case Peer.REQUEST:                     
                    bb = ByteBuffer.wrap(qp.getData());
                    sessionId = bb.getLong();
                    cxid = bb.getInt();
                    type = bb.getInt();
                    bb = bb.slice();
                    Request si;
                    if(type == OpCode.sync){
                        si = new PeerLearnerSyncRequest(this, sessionId, cxid, type, bb, qp.getAuthinfo());
                    } else {
                        si = new Request(null, sessionId, cxid, type, bb, qp.getAuthinfo());
                    }
                    si.setOwner(this);
                    peer.zk.submitRequest(si);
                    break;
                default:
                }
            }
        } catch (IOException e) {
            if (sock != null && !sock.isClosed()) {
                LOG.error("Unexpected exception causing shutdown while sock "
                        + "still open", e);
            	//close the socket to make sure the 
            	//other side can see it being close
            	try {
            		sock.close();
            	} catch(IOException ie) {
            		// do nothing
            	}
            }
        } catch (InterruptedException e) {
            LOG.error("Unexpected exception causing shutdown", e);
        } finally {
            LOG.warn("******* GOODBYE " 
                    + (sock != null ? sock.getRemoteSocketAddress() : "<null>")
                    + " ********");
            // Send the packet of death
            try {
                queuedPackets.put(proposalOfDeath);
            } catch (InterruptedException e) {
                LOG.warn("Ignoring unexpected exception", e);
            }
            shutdown();
        }
    }

    public void shutdown() {
        try {
            if (sock != null && !sock.isClosed()) {
                sock.close();
            }
        } catch (IOException e) {
            LOG.warn("Ignoring unexpected exception during socket close", e);
        }
        this.interrupt();
        peer.removeLearnerHandler(this);
    }

    public long tickOfLastAck() {
        return tickOfLastAck;
    }

    /**
     * ping calls from the peer to the peers
     */
    public void ping() {
        long id;
        synchronized(peer) {
            id = peer.lastProposed;
        }
        QuorumPacket ping = new QuorumPacket(Peer.PING, id,
                null, null);
        queuePacket(ping);
    }

    void queuePacket(QuorumPacket p) {
        queuedPackets.add(p);
    }

    public boolean synced() {
        return isAlive()
        && tickOfLastAck >= peer.self.tick - peer.self.syncLimit;
    }
}
