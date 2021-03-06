package com.mpnet.bitswarm.sessions;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @ClassName: Session
 * @Description: TODO(这里用一句话描述这个类的作用)
 * @author daixiwei daixiwei15@126.com
 * @date 2015年2月5日 下午1:23:56
 *
 */
public final class Session implements ISession {
	public static final String		ENCRYPTION_ENABLED	= "session_encryption_enabled";
	public static final String		DATA_BUFFER			= "session_data_buffer";
	public static final String		PROTOCOL			= "session_protocol";
	public static final String		NO_IP				= "NO_IP";
	public static final String		BBCLIENT			= "bbClient";
	public static final String		WS_CHANNEL			= "wsChannel";
	public static final String		PACKET_READ_STATE	= "read_state";
	private static AtomicInteger	idCounter			= new AtomicInteger(0);
	
	private volatile long			readBytes			= 0L;
	private volatile long			writtenBytes		= 0L;
	private volatile int			droppedMessages		= 0;
	private SocketChannel			connection;
	private DatagramChannel			datagramChannel;
	private volatile long			creationTime;
	private volatile long			lastReadTime;
	private volatile long			lastWriteTime;
	private volatile long			lastActivityTime;
	private volatile long			lastLoggedInActivityTime;
	private int						id;
	private String					hashId;
	private String					nodeId;
	private SessionType				type;
	private volatile String			clientIpAddress;
	private volatile int			clientPort;
	private int						serverPort;
	private String					serverAddress;
	private int						maxIdleTime;
	private int						maxLoggedInIdleTime;
	private volatile int			reconnectionSeconds;
	private volatile long			freezeTime			= 0L;
	private volatile boolean		frozen				= false;
	private boolean					markedForEviction	= false;
	private volatile boolean		connected			= false;
	private volatile boolean		loggedIn			= false;
	private IPacketQueue			packetQueue;
	private ISessionManager			sessionManager;
	private Map<String, Object>		systemProperties;
	private Map<String, Object>		properties;
	
	public Session() {
		creationTime = lastReadTime = lastWriteTime = lastActivityTime = System.currentTimeMillis();
		
		setId(getUniqueId());
		
		setHashId("---");
		
		properties = new ConcurrentHashMap<String, Object>();
		systemProperties = new ConcurrentHashMap<String, Object>();
	}
	
	private static int getUniqueId() {
		return idCounter.incrementAndGet();
	}
	
	public void addReadBytes(long amount) {
		this.readBytes += amount;
	}
	
	public void addWrittenBytes(long amount) {
		this.writtenBytes += amount;
	}
	
	public SocketChannel getConnection() {
		return this.connection;
	}
	
	public DatagramChannel getDatagramChannel() {
		return this.datagramChannel;
	}
	
	public void setDatagrmChannel(DatagramChannel channel) {
		this.datagramChannel = channel;
	}
	
	public long getCreationTime() {
		return this.creationTime;
	}
	
	public String getHashId() {
		return this.hashId;
	}
	
	public int getId() {
		return this.id;
	}
	
	public String getFullIpAddress() {
		return clientPort > 0 ? getAddress() + ":" + clientPort : getAddress();
	}
	
	public String getAddress() {
		
		return this.clientIpAddress;
	}
	
	public int getClientPort() {
		return this.clientPort;
	}
	
	public int getServerPort() {
		return this.serverPort;
	}
	
	public String getFullServerIpAddress() {
		return this.serverAddress + ":" + this.serverPort;
	}
	
	public String getServerAddress() {
		return this.serverAddress;
	}
	
	public long getLastActivityTime() {
		return this.lastActivityTime;
	}
	
	public long getLastReadTime() {
		return this.lastReadTime;
	}
	
	public long getLastWriteTime() {
		return this.lastWriteTime;
	}
	
	public int getMaxIdleTime() {
		return this.maxIdleTime;
	}
	
	public IPacketQueue getPacketQueue() {
		return this.packetQueue;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public Object getProperty(String key) {
		return this.properties.get(key);
	}
	
	public void removeProperty(String key) {
		this.properties.remove(key);
	}
	
	public long getReadBytes() {
		return this.readBytes;
	}
	
	public Object getSystemProperty(String key) {
		return this.systemProperties.get(key);
	}
	
	public void removeSystemProperty(String key) {
		this.systemProperties.remove(key);
	}
	
	public SessionType getType() {
		return this.type;
	}
	
	public long getWrittenBytes() {
		
		return this.writtenBytes;
	}
	
	public boolean isConnected() {
		return this.connected;
	}
	
	public void setConnected(boolean value) {
		this.connected = value;
	}
	
	public boolean isLoggedIn() {
		return this.loggedIn;
	}
	
	public void setLoggedIn(boolean value) {
		this.loggedIn = value;
	}
	
	public int getMaxLoggedInIdleTime() {
		return this.maxLoggedInIdleTime;
	}
	
	public void setMaxLoggedInIdleTime(int idleTime) {
		if (idleTime < this.maxIdleTime) {
			idleTime = this.maxIdleTime + 60;
		}
		
		this.maxLoggedInIdleTime = idleTime;
	}
	
	public long getLastLoggedInActivityTime() {
		return this.lastLoggedInActivityTime;
	}
	
	public void setLastLoggedInActivityTime(long timestamp) {
		this.lastLoggedInActivityTime = timestamp;
	}
	
	public boolean isLocal() {
		return true;
	}
	
	public boolean isIdle() {
		if (this.loggedIn) {
			return isLoggedInIdle();
		}
		return isSocketIdle();
	}
	
	private boolean isSocketIdle() {
		boolean isIdle = false;
		
		if (this.maxIdleTime > 0) {
			long elapsedSinceLastActivity = System.currentTimeMillis() - this.lastActivityTime;
			isIdle = elapsedSinceLastActivity / 1000L > this.maxIdleTime;
		}
		
		return isIdle;
	}
	
	private boolean isLoggedInIdle() {
		boolean isIdle = false;
		
		if (this.maxLoggedInIdleTime > 0) {
			long elapsedSinceLastActivity = System.currentTimeMillis() - this.lastLoggedInActivityTime;
			isIdle = elapsedSinceLastActivity / 1000L > this.maxLoggedInIdleTime;
		}
		
		return isIdle;
	}
	
	public boolean isMarkedForEviction() {
		return this.markedForEviction;
	}
	
	public void setConnection(SocketChannel connection) {
		if (connection == null) {
			reconnectionDestroy();
			return;
		}
		
		if (this.reconnectionSeconds > 0) {
			setSocketConnection(connection);
		} else {
			if (this.connection != null) {
				throw new IllegalArgumentException("You cannot overwrite the connection linked to a Session!");
			}
			setSocketConnection(connection);
		}
	}
	
	private void setSocketConnection(SocketChannel connection) {
		this.connection = connection;
		this.serverPort = connection.socket().getLocalPort();
		this.serverAddress = connection.socket().getLocalAddress().toString().substring(1);
		
		if ((connection != null) && (connection.socket() != null) && (!connection.socket().isClosed())) {
			String hostAddr = connection.socket().getRemoteSocketAddress().toString().substring(1);
			String[] adr = hostAddr.split("\\:");
			this.clientIpAddress = adr[0];
			try {
				this.clientPort = Integer.parseInt(adr[1]);
			} catch (NumberFormatException localNumberFormatException) {}
			this.connected = true;
		} else {
			this.clientIpAddress = "[unknown]";
		}
	}
	
	private void reconnectionDestroy() {
		this.packetQueue = null;
		this.connection = null;
		
		this.sessionManager.removeSession(this);
	}
	
	public void setPacketQueue(IPacketQueue queue) {
		if (this.packetQueue != null) {
			throw new IllegalStateException("Cannot reassing the packet queue. Queue already exists!");
		}
		this.packetQueue = queue;
	}
	
	public void setCreationTime(long timestamp) {
		this.creationTime = timestamp;
	}
	
	public void setHashId(String hash) {
		this.hashId = hash;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public void setLastActivityTime(long timestamp) {
		this.lastActivityTime = timestamp;
	}
	
	public void setLastReadTime(long timestamp) {
		this.lastReadTime = (this.lastActivityTime = timestamp);
	}
	
	public void setLastWriteTime(long timestamp) {
		this.lastWriteTime = (this.lastActivityTime = timestamp);
	}
	
	public void setMarkedForEviction() {
		this.markedForEviction = true;
		
		this.reconnectionSeconds = 0;
	}
	
	public void setMaxIdleTime(int idleTime) {
		this.maxIdleTime = idleTime;
	}
	
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setProperty(String key, Object property) {
		this.properties.put(key, property);
	}
	
	public void setSystemProperty(String key, Object property) {
		this.systemProperties.put(key, property);
	}
	
	public void setType(SessionType type) {
		this.type = type;
		
		if (type == SessionType.VOID) {
			this.clientIpAddress = "NO_IP";
			this.clientPort = 0;
		}
	}
	
	public int getDroppedMessages() {
		return this.droppedMessages;
	}
	
	public void addDroppedMessages(int amount) {
		this.droppedMessages += amount;
	}
	
	public ISessionManager getSessionManager() {
		return this.sessionManager;
	}
	
	public void setSessionManager(ISessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}
	
	public boolean isFrozen() {
		return this.frozen;
	}
	
	public synchronized void freeze() {
		this.frozen = true;
		this.freezeTime = System.currentTimeMillis();
	}
	
	public synchronized void unfreeze() {
		this.frozen = false;
		this.freezeTime = 0L;
	}
	
	public long getFreezeTime() {
		return this.freezeTime;
	}
	
	public boolean isReconnectionTimeExpired() {
		long expiry = this.freezeTime + 1000 * this.reconnectionSeconds;
		
		return System.currentTimeMillis() > expiry;
	}
	
	public void close() throws IOException {
		this.packetQueue = null;
		try {
			if ((this.type == SessionType.DEFAULT) && (this.connection != null)) {
				Socket socket = this.connection.socket();
				
				if ((socket != null) && (!socket.isClosed())) {
					socket.shutdownInput();
					socket.shutdownOutput();
					socket.close();
					this.connection.close();
				}
				
				this.datagramChannel = null;
			} else if (this.type == SessionType.WEBSOCKET) {}
			
		} finally {
			this.connected = false;
			this.sessionManager.removeSession(this);
		}
	}
	
	public int getReconnectionSeconds() {
		return this.reconnectionSeconds;
	}
	
	public void setReconnectionSeconds(int value) {
		if (value < 0)
			this.reconnectionSeconds = 0;
		else
			this.reconnectionSeconds = value;
	}
	
	public String toString() {
		return String.format("{ Id: %s, Type: %s, Logged: %s, IP: %s }", id, type, loggedIn ? "Yes" : "No", getFullIpAddress());
	}
	
	public boolean equals(Object obj) {
		if (!(obj instanceof ISession)) {
			return false;
		}
		boolean isEqual = false;
		ISession session = (ISession) obj;
		
		if (session.getId() == this.id) {
			isEqual = true;
		}
		return isEqual;
	}
}