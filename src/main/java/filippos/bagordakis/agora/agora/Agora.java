package filippos.bagordakis.agora.agora;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import filippos.bagordakis.agora.agora.data.dto.AgoraEvent;
import filippos.bagordakis.agora.agora.data.dto.BaseDTO;
import filippos.bagordakis.agora.agora.data.dto.GreetingDTO;
import filippos.bagordakis.agora.agora.data.dto.HeartbeatDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Agora {

	@Value("localhost")
	private String SERVER_ADDRESS;
	@Value("12345")
	private int SERVER_PORT;
	@Value("1000")
	private int RECONNECT_DELAY;

	private Socket socket = null;

	private PrintWriter out = null;
	private BufferedReader in = null;

	private final long HEARTBEAT_INTERVAL = 10000;
	private final long HEARTBEAT_TIMEOUT = 20000;
	private long receivedHeartbeatTime = System.currentTimeMillis();

	private boolean running;
	private boolean connected;

	private static final Logger log = LoggerFactory.getLogger(Agora.class);

	private ConcurrentLinkedQueue<Object> que;

	private final ObjectMapper objectMapper = AgoraHelper.getObjectMapper();

	@Value("Athens")
	private String id;

	public Agora() {
		que = new ConcurrentLinkedQueue<>();
	}

	@EventListener
	protected void addToQue(AgoraEvent agoraEvent) {
		que.add(agoraEvent.getData());
	}

	@PostConstruct
	public void connect() throws InterruptedException {

		objectMapper.registerSubtypes(new NamedType(GreetingDTO.class, "greeting"),
				new NamedType(HeartbeatDTO.class, "heartbeat"));
		running = true;

		establishConnection();

		new Thread(new Listener()).start();
		new Thread(new Writer()).start();

	}

	@PreDestroy
	public void close() {

		running = false;

		closeConnections();
	}

	private void closeConnections() {
		connected = false;
		while (true) {
			try {
				if (socket != null) {
					socket.close();
					log.info("Closing connection to Agora");
					socket = null;
				}
				if (out != null) {
					out.close();
					out = null;
				}
				if (in != null) {
					in.close();
					in = null;
				}

				break;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private synchronized void establishConnection() {
		closeConnections();
		acquireSocket();
		connectPrintWriter();
		getBufferedReader();
		receivedHeartbeatTime = System.currentTimeMillis();
		connected = true;
	}

	private void connectPrintWriter() {
		while (running) {
			try {
				out = new PrintWriter(socket.getOutputStream(), true);
				break;
			} catch (IOException e) {
				log.error("{}", e.getMessage());
			}
		}
	}

	private void getBufferedReader() {
		while (running) {
			try {
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				break;
			} catch (IOException e) {
				log.error("{}", e.getMessage());
			}
		}
	}

	private void acquireSocket() {
		while (running) {
			try {

				socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
				if (socket.isConnected()) {
					log.info("Connected to Agora at  [{}]:[{}]", SERVER_ADDRESS, SERVER_PORT);
					break;
				}
			} catch (UnknownHostException e) {
				log.error("Unkown host  {}", e.getMessage());
				throw new RuntimeException(e);
			} catch (IOException e) {
				log.error("Failed to connect to Agora at [{}]:[{}] with message : {}", SERVER_ADDRESS, SERVER_PORT,
						e.getMessage());
				try {
					Thread.sleep(RECONNECT_DELAY);
				} catch (InterruptedException e2) {
					throw new RuntimeException(e2);
				}
			}
		}
	}

	private class Listener implements Runnable {

		@Override
		public void run() {

			log.info("Agora Listener started");

			while (running) {

				while (connected) {
					String receivedJson;
					try {
						while ((receivedJson = in.readLine()) != null) {
							BaseDTO dto = objectMapper.readValue(receivedJson, BaseDTO.class);
							if (dto instanceof HeartbeatDTO) {
								log.info("Heartbeat received");
								receivedHeartbeatTime = System.currentTimeMillis();
							} else {
								log.info("Received object [{}] over TCP", receivedJson);
							}
						}
					} catch (IOException e) {
					}
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}

	}

	private class Writer implements Runnable {

		@Override
		public void run() {

			log.info("Agora Writer started");

			while (running) {
				boolean shouldGreet = true;
				while (connected) {

					if (!socket.isConnected() || socket.isClosed()) {
						running = false;
						log.error("Socket is not connected or closed. Stopping sending data.");
						break;
					}

					try {
						if (shouldGreet) {
							out.println(
									objectMapper.writeValueAsString(new GreetingDTO(UUID.randomUUID().toString(), id)));
							shouldGreet = false;
						} else {
							Object object = que.poll();
							if (object != null) {
								String json = objectMapper.writeValueAsString(object);
								out.println(json);
								log.info("Sent object [{}] over TCP", json);
							}
						}

						if (System.currentTimeMillis() % HEARTBEAT_INTERVAL == 0) {
							String id = UUID.randomUUID().toString();
							String json = objectMapper.writeValueAsString(new HeartbeatDTO(id));
							out.println(json);
							log.info("Heartbeat [{}] sent to [{}]", id, socket.getRemoteSocketAddress().toString());
						}

					} catch (IOException e) {
						log.error("Failed to serialize and send object", e);
					}

					if (System.currentTimeMillis() - receivedHeartbeatTime > HEARTBEAT_TIMEOUT) {
						establishConnection();
					}
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}
		}
	}

}
