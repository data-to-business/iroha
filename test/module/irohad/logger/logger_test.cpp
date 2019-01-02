/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include "logger/logger.hpp"
#include <gtest/gtest.h>

#include <vector>

TEST(LoggerTest, basicStandaloneLoggerTest) {
  logger::LoggerConfig config;
  config.log_level = logger::LogLevel::kInfo;
  auto one_logger = logger::Logger(
      "standalone_logger", config, logger::LoggerThreadSafety::kSingleThread);
  one_logger.trace("testing a standalone logger: trace");
  one_logger.info("testing a standalone logger: info");
  one_logger.error("testing a standalone logger: error");
}

TEST(LoggerTest, boolReprTest) {
  ASSERT_EQ("true", logger::boolRepr(true));
  ASSERT_EQ("false", logger::boolRepr(false));
}

TEST(LoggerTest, logBoolTest) {
  ASSERT_EQ("true", logger::logBool(1));
  ASSERT_EQ("false", logger::boolRepr((void *)nullptr));
}

TEST(LoggerTest, collectionToStringNotEmpty) {
  std::vector<int> collection{1, 2, 3};
  auto res = logger::to_string(collection,
                               [](auto val) { return std::to_string(val); });
  ASSERT_EQ("{1, 2, 3}", res);
}

TEST(LoggerTest, collectionToStringEmpty) {
  std::vector<int> collection{};
  auto res = logger::to_string(collection,
                               [](auto val) { return std::to_string(val); });
  ASSERT_EQ("{}", res);
}
