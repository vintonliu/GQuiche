// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "quiche/quic/tools/quic_toy_server.h"

#include <utility>
#include <vector>

#include "absl/container/flat_hash_set.h"
#include "absl/strings/str_split.h"
#include "absl/strings/string_view.h"
#include "quiche/quic/core/quic_server_id.h"
#include "quiche/quic/core/quic_versions.h"
#include "quiche/quic/platform/api/quic_default_proof_providers.h"
#include "quiche/quic/platform/api/quic_socket_address.h"
#include "quiche/quic/tools/connect_server_backend.h"
#include "quiche/quic/tools/quic_memory_cache_backend.h"
#include "quiche/common/platform/api/quiche_command_line_flags.h"
#include "quiche/common/platform/api/quiche_logging.h"

DEFINE_QUICHE_COMMAND_LINE_FLAG(int32_t, port, 6121,
                                "The port the quic server will listen on.");

DEFINE_QUICHE_COMMAND_LINE_FLAG(
    std::string, quic_response_cache_dir, "",
    "Specifies the directory used during QuicHttpResponseCache "
    "construction to seed the cache. Cache directory can be "
    "generated using `wget -p --save-headers <url>`");

DEFINE_QUICHE_COMMAND_LINE_FLAG(
    bool, generate_dynamic_responses, false,
    "If true, then URLs which have a numeric path will send a dynamically "
    "generated response of that many bytes.");

DEFINE_QUICHE_COMMAND_LINE_FLAG(bool, quic_ietf_draft, false,
                                "Only enable IETF draft versions. This also "
                                "enables required internal QUIC flags.");

DEFINE_QUICHE_COMMAND_LINE_FLAG(
    std::string, quic_versions, "",
    "QUIC versions to enable, e.g. \"h3-25,h3-27\". If not set, then all "
    "available versions are enabled.");

DEFINE_QUICHE_COMMAND_LINE_FLAG(bool, enable_webtransport, false,
                                "If true, WebTransport support is enabled.");

DEFINE_QUICHE_COMMAND_LINE_FLAG(
    std::string, connect_proxy_destinations, "",
    "Specifies a comma-separated list of destinations (\"hostname:port\") to "
    "which the quic server will allow tunneling via CONNECT.");

namespace quic {

std::unique_ptr<quic::QuicSimpleServerBackend>
QuicToyServer::MemoryCacheBackendFactory::CreateBackend() {
  auto memory_cache_backend = std::make_unique<QuicMemoryCacheBackend>();
  if (quiche::GetQuicheCommandLineFlag(FLAGS_generate_dynamic_responses)) {
    memory_cache_backend->GenerateDynamicResponses();
  }
  if (!quiche::GetQuicheCommandLineFlag(FLAGS_quic_response_cache_dir)
           .empty()) {
    memory_cache_backend->InitializeBackend(
        quiche::GetQuicheCommandLineFlag(FLAGS_quic_response_cache_dir));
  }
  if (quiche::GetQuicheCommandLineFlag(FLAGS_enable_webtransport)) {
    memory_cache_backend->EnableWebTransport();
  }

  if (!quiche::GetQuicheCommandLineFlag(FLAGS_connect_proxy_destinations)
           .empty()) {
    absl::flat_hash_set<QuicServerId> connect_proxy_destinations;
    for (absl::string_view destination : absl::StrSplit(
             quiche::GetQuicheCommandLineFlag(FLAGS_connect_proxy_destinations),
             ',', absl::SkipEmpty())) {
      absl::optional<QuicServerId> destination_server_id =
          QuicServerId::ParseFromHostPortString(destination);
      QUICHE_CHECK(destination_server_id.has_value());
      connect_proxy_destinations.insert(
          std::move(destination_server_id).value());
    }
    QUICHE_CHECK(!connect_proxy_destinations.empty());

    return std::make_unique<ConnectServerBackend>(
        std::move(memory_cache_backend), std::move(connect_proxy_destinations));
  }

  return memory_cache_backend;
}

QuicToyServer::QuicToyServer(BackendFactory* backend_factory,
                             ServerFactory* server_factory)
    : backend_factory_(backend_factory), server_factory_(server_factory) {}

int QuicToyServer::Start() {
  ParsedQuicVersionVector supported_versions;
  if (quiche::GetQuicheCommandLineFlag(FLAGS_quic_ietf_draft)) {
    QuicVersionInitializeSupportForIetfDraft();
    for (const ParsedQuicVersion& version : AllSupportedVersions()) {
      // Add all versions that supports IETF QUIC.
      if (version.HasIetfQuicFrames() &&
          version.handshake_protocol == quic::PROTOCOL_TLS1_3) {
        supported_versions.push_back(version);
      }
    }
  } else {
    supported_versions = AllSupportedVersions();
  }
  std::string versions_string =
      quiche::GetQuicheCommandLineFlag(FLAGS_quic_versions);
  if (!versions_string.empty()) {
    supported_versions = ParseQuicVersionVectorString(versions_string);
  }
  if (supported_versions.empty()) {
    return 1;
  }
  for (const auto& version : supported_versions) {
    QuicEnableVersion(version);
  }
  auto proof_source = quic::CreateDefaultProofSource();
  auto backend = backend_factory_->CreateBackend();
  auto server = server_factory_->CreateServer(
      backend.get(), std::move(proof_source), supported_versions);

  if (!server->CreateUDPSocketAndListen(quic::QuicSocketAddress(
          quic::QuicIpAddress::Any6(),
          quiche::GetQuicheCommandLineFlag(FLAGS_port)))) {
    return 1;
  }

  server->HandleEventsForever();
  return 0;
}

}  // namespace quic
