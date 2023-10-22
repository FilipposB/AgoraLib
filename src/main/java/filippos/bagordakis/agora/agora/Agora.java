package filippos.bagordakis.agora.agora;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import filippos.bagordakis.agora.agora.data.event.AgoraEvent;
import filippos.bagordakis.agora.common.dto.AckoledgmentDTO;
import filippos.bagordakis.agora.common.dto.BaseDTO;
import filippos.bagordakis.agora.common.dto.GreetingDTO;
import filippos.bagordakis.agora.common.dto.HeartbeatDTO;
import filippos.bagordakis.agora.common.dto.RequestDTO;
import filippos.bagordakis.agora.common.request.cache.AgoraRequestCache;
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

	private ObjectOutputStream out = null;
	private ObjectInputStream in = null;

	private final long HEARTBEAT_INTERVAL = 1000;
	private final long HEARTBEAT_TIMEOUT = 10 * HEARTBEAT_INTERVAL;
	private long receivedHeartbeatTime = System.currentTimeMillis();

	private boolean running;
	private boolean connected;

	private static final Logger log = LoggerFactory.getLogger(Agora.class);

	private static ConcurrentLinkedQueue<RequestDTO> que;

	private static boolean shouldGreet = true;

	private static final AgoraRequestCache cache = new AgoraRequestCache(Duration.ofMillis(1000), x -> {
		if (x instanceof RequestDTO dto) {
			log.info("Didnt hear back will reque !");
			que.add(dto);
		} else if (x instanceof GreetingDTO dto) {
			log.info("Didnt hear back will greet again !");
			shouldGreet = true;
		}
	});

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
		running = true;

		log.atInfo();

		new Thread(() -> {
			establishConnection();
			new Thread(new Listener()).start();
			new Thread(new Writer()).start();
		}).start();

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
		shouldGreet = true;
		connected = true;
	}

	private void connectPrintWriter() {
		while (running) {
			try {
				out = new ObjectOutputStream(socket.getOutputStream());
				break;
			} catch (IOException e) {
				log.error("{}", e.getMessage());
			}
		}
	}

	private void getBufferedReader() {
		while (running) {
			try {
				in = new ObjectInputStream(socket.getInputStream());
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
					
					
					BaseDTO dto;
					try {
						while ((dto = (BaseDTO) in.readObject()) != null) {

							if (dto instanceof HeartbeatDTO) {
								log.debug("Heartbeat received");
								receivedHeartbeatTime = System.currentTimeMillis();
							} else if (dto instanceof AckoledgmentDTO ackoledgmentDTO) {
								BaseDTO baseDTO = cache.remove(ackoledgmentDTO);

								if (baseDTO != null) {
									log.info("Received acknoledgement for {}", baseDTO.toString());
								}

							} else {
								log.info("Received object [{}] over TCP", dto.toString());
							}
						}
					} catch (IOException e) {
						log.error("{}", e.getMessage());
						establishConnection();
					} catch (ClassNotFoundException e) {
						log.error("{}", e.getMessage());
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

		private long lastHeartbeatSent = System.currentTimeMillis();

		@Override
		public void run() {

			log.info("Agora Writer started");

			while (running) {
				while (connected) {

					if (!socket.isConnected() || socket.isClosed()) {
						running = false;
						log.error("Socket is not connected or closed. Stopping sending data.");
						break;
					}

					try {

						if (shouldGreet) {
							GreetingDTO dto = new GreetingDTO(UUID.randomUUID(), id);
							out.writeObject(dto);
							cache.put(dto);
							shouldGreet = false;
						} else {
							RequestDTO requestDTO = que.poll();
							if (requestDTO != null) {

								out.writeObject(requestDTO);
								cache.put(requestDTO);

								log.info("Sent object [{}] over TCP", requestDTO.toString());
							}
						}
						
						if (System.currentTimeMillis() - lastHeartbeatSent >= HEARTBEAT_INTERVAL) {
							HeartbeatDTO heartbeatDTO = HeartbeatDTO.newInstance();
							out.writeObject(heartbeatDTO);
							lastHeartbeatSent = System.currentTimeMillis();
							log.debug("Heartbeat [{}] sent to [{}]", heartbeatDTO.getId(),
									socket.getRemoteSocketAddress().toString());
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
