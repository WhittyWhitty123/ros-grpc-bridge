package org.athenian;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import org.athenian.common.Utils;
import org.athenian.core.RosClientOptions;
import org.athenian.core.TwistDataStream;
import org.athenian.grpc.RosBridgeServiceGrpc.RosBridgeServiceBlockingStub;
import org.athenian.grpc.RosBridgeServiceGrpc.RosBridgeServiceStub;
import org.athenian.grpc.TwistData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.grpc.ClientInterceptors.intercept;
import static org.athenian.grpc.RosBridgeServiceGrpc.newBlockingStub;
import static org.athenian.grpc.RosBridgeServiceGrpc.newStub;

public class RosClient {
  private static final Logger logger = LoggerFactory.getLogger(RosClient.class);

  private final AtomicReference<ManagedChannel>               channelRef      = new AtomicReference<>();
  private final AtomicReference<RosBridgeServiceBlockingStub> blockingStubRef = new AtomicReference<>();
  private final AtomicReference<RosBridgeServiceStub>         asyncStubRef    = new AtomicReference<>();

  private final String hostname;
  private final int    port;

  public RosClient(final RosClientOptions options, final String inProcessName) {
    if (options.getHostname().contains(":")) {
      String[] vals = options.getHostname().split(":");
      this.hostname = vals[0];
      this.port = Integer.valueOf(vals[1]);
    }
    else {
      this.hostname = options.getHostname();
      this.port = 50051;
    }

    this.channelRef.set(isNullOrEmpty(inProcessName) ? NettyChannelBuilder.forAddress(this.hostname, this.port)
                                                                          .usePlaintext(true)
                                                                          .build()
                                                     : InProcessChannelBuilder.forName(inProcessName)
                                                                              .usePlaintext(true)
                                                                              .build());
    logger.info("Creating gRPC stubs");
    this.blockingStubRef.set(newBlockingStub(intercept(this.getChannel())));
    this.asyncStubRef.set(newStub(intercept(this.getChannel())));
  }

  public static void main(final String[] argv) {
    final RosClientOptions options = new RosClientOptions(argv);
    final RosClient rosClient = new RosClient(options, null);

    int count = 10000;
    for (int i = 0; i < count; i++) {
      TwistData data = TwistData.newBuilder()
                                .setLinearX(i)
                                .setLinearY(i + 1)
                                .setLinearZ(i + 2)
                                .setAngularX(i + 3)
                                .setAngularY(i + 4)
                                .setAngularZ(i + 5)
                                .build();
      rosClient.writeData(data);
    }

    try (final TwistDataStream stream = rosClient.newTwistDataStream()) {
      for (int i = 0; i < count; i++) {
        TwistData data = TwistData.newBuilder()
                                  .setLinearX(i)
                                  .setLinearY(i + 1)
                                  .setLinearZ(i + 2)
                                  .setAngularX(i + 3)
                                  .setAngularY(i + 4)
                                  .setAngularZ(i + 5)
                                  .build();
        logger.info("Writing data");
        stream.writeData(data);
      }
    }
    Utils.sleepForSecs(2);
  }

  public void writeData(final TwistData data) {
    this.getBlockingStub().writeTwistData(data);
  }

  public TwistDataStream newTwistDataStream() {
    return new TwistDataStream(this.getAsyncStub());
  }

  private ManagedChannel getChannel() { return this.channelRef.get(); }

  private RosBridgeServiceBlockingStub getBlockingStub() { return this.blockingStubRef.get(); }

  private RosBridgeServiceStub getAsyncStub() { return this.asyncStubRef.get(); }
}