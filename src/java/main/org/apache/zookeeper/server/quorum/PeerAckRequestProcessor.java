// Modified from AckRequesProcessor
//Sagar Chordia
package org.apache.zookeeper.server.quorum;

import org.apache.log4j.Logger;

import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.RequestProcessor;


/**
 * This is a very simple RequestProcessor that simply forwards a request from a
 * previous stage to the leader as an ACK.
 */
class PeerAckRequestProcessor implements RequestProcessor {
    private static final Logger LOG = Logger.getLogger(PeerAckRequestProcessor.class);
    Peer peer;

    PeerAckRequestProcessor(Peer peer) {
        this.peer = peer;
    }

    /**
     * Forward the request as an ACK to the leader
     */
    public void processRequest(Request request) {
        QuorumPeer self = peer.self;
        if(self != null)
            peer.processAck(self.getId(), request.zxid, null);
        else
            LOG.error("Null QuorumPeer");
    }

    public void shutdown() {
        // XXX No need to do anything
    }
}
