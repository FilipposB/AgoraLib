package filippos.bagordakis.agora.agora;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
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

	private final long HEARTBEAT_INTERVAL = 5000; // 5 seconds
	private final long HEARTBEAT_TIMEOUT = 10000; // 10 seconds
	private boolean receivedHeartbeatAck = true;

	private boolean running = true;

	private static final Logger log = LoggerFactory.getLogger(Agora.class);

	@Value("Athens")
	private String id;

	private ConcurrentLinkedQueue<Object> que;

	public Agora() {
		que = new ConcurrentLinkedQueue<>();
	}

	@EventListener
	protected void addToQue(AgoraEvent agoraEvent) {
		que.add(agoraEvent.getData());
	}

	@PostConstruct
	public void connect() throws InterruptedException {

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerSubtypes(new NamedType(GreetingDTO.class, "greeting"),
				new NamedType(HeartbeatDTO.class, "heartbeat"));

		close();

		running = true;

		while (running) {
			try {
				socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
				out = new PrintWriter(socket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				log.info("Connected to server: [{}]", socket.getRemoteSocketAddress());

				// Thread for reading from server
				new Thread(() -> {
					String receivedJson;
					try {
						while ((receivedJson = in.readLine()) != null) {
							BaseDTO dto = objectMapper.readValue(receivedJson, BaseDTO.class);
							if (dto instanceof HeartbeatDTO) {
								receivedHeartbeatAck = true;
							}
							log.info("Received object [{}] over TCP", receivedJson);
						}
					} catch (IOException e) {
						log.error("Failed to read from server", e);
						running = false;
					}
				}).start();

				// Thread for writing to server
				new Thread(() -> {
					while (running) {
						if (!socket.isConnected() || socket.isClosed()) {
							running = false;
							log.error("Socket is not connected or closed. Stopping sending data.");
							break;
						}
						Object object = que.poll();
						if (object != null) {
							try {
								String json = objectMapper.writeValueAsString(object);
								out.println(json);
								log.info("Sent object [{}] over TCP", json);
							} catch (IOException e) {
								log.error("Failed to serialize and send object", e);
								running = false;
							}
						}

						if (System.currentTimeMillis() % HEARTBEAT_INTERVAL == 0) {
							try {
								String json = objectMapper.writeValueAsString(new HeartbeatDTO());
								out.println(json);
								receivedHeartbeatAck = false;
								// Start a timer to check for heartbeat acknowledgment
								if (!receivedHeartbeatAck) {
									try {
										connect();
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}

							} catch (JsonProcessingException e) {
								e.printStackTrace();
							}

						}
					}
				}).start();

				break;

			} catch (IOException e) {
				log.error("Failed to connect to server at [{}] and port [{}]", SERVER_ADDRESS, SERVER_PORT);
				try {
					Thread.sleep(RECONNECT_DELAY);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
			}

		}

	}

	@PreDestroy
	public void close() {

		running = false;

		while (true) {
			try {
				if (socket != null) {
					socket.close();
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

}
