/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include "logger/logger_spdlog.hpp"

#include <mutex>
#include <regex>

#define SPDLOG_FMT_EXTERNAL

#include <spdlog/sinks/stdout_color_sinks.h>
#include <spdlog/spdlog.h>
#include <boost/assert.hpp>

namespace {

  const std::string kDefaultPattern = R"([%Y-%m-%d %H:%M:%S.%F][%6h][%L][%n]: %v)";

  spdlog::level::level_enum getSpdlogLogLevel(logger::LogLevel level) {
    static const std::map<logger::LogLevel, const spdlog::level::level_enum>
        kSpdLogLevels = {
            {logger::LogLevel::kTrace, spdlog::level::trace},
            {logger::LogLevel::kDebug, spdlog::level::debug},
            {logger::LogLevel::kInfo, spdlog::level::info},
            {logger::LogLevel::kWarn, spdlog::level::warn},
            {logger::LogLevel::kError, spdlog::level::err},
            {logger::LogLevel::kCritical, spdlog::level::critical}};
    const auto it = kSpdLogLevels.find(level);
    BOOST_ASSERT_MSG(it != kSpdLogLevels.end(), "Unknown log level!");
    return it == kSpdLogLevels.end()
        ? kSpdLogLevels.at(logger::kDefaultLogLevel)
        : it->second;
  }

  std::string replaceGitHashes(std::string format) {
    static const std::regex hash_regex{R"(\%([0-9]{0,2})h)"};
    std::string replaced;
    std::smatch match;
    auto pos = format.cbegin();
    while (std::regex_search(pos, format.cend(), match, hash_regex)) {
      assert(not match.empty());
      replaced.append(std::string{pos, match[0].first});
      std::string git_hash{GIT_REPO_HEAD_HASH};
      if (match.size() > 1 and match[1].length() > 0) {
        git_hash.resize(std::stoi(match[1].str()), ' ');
      }
      replaced.append(std::move(git_hash));
      pos = match[0].second;
    }
    replaced.append(std::move(format).substr(pos - format.begin()));
    return replaced;
  }

}  // namespace

namespace logger {

  const LogPatterns kDefaultLogPatterns = ([] {
    LogPatterns p;
    p.setPattern(LogLevel::kTrace,
                 R"([%Y-%m-%d %H:%M:%S.%F][%6h][th:%t][%5l][%n]: %v)");
    p.setPattern(LogLevel::kInfo, kDefaultPattern);
    return p;
  })();

  void LogPatterns::setPattern(LogLevel level, std::string pattern) {
    patterns_[level] = replaceGitHashes(std::move(pattern));
  }

  std::string LogPatterns::getPattern(LogLevel level) const {
    for (auto it = patterns_.rbegin(); it != patterns_.rend(); ++it) {
      if (it->first <= level) {
        return it->second;
      }
    }
    return kDefaultPattern;
  }

  LoggerSpdlog::LoggerSpdlog(std::string tag, ConstLoggerConfigPtr config)
      : tag_(tag),
        config_(std::move(config)),
        logger_(spdlog::stdout_color_mt(tag)) {
    setupLogger();
  }

  void LoggerSpdlog::setupLogger() {
    logger_->set_level(getSpdlogLogLevel(config_->log_level));
    logger_->set_pattern(config_->patterns.getPattern(config_->log_level));
  }

  void LoggerSpdlog::logInternal(Level level, const std::string &s) const {
    logger_->log(getSpdlogLogLevel(config_->log_level), s);
  }

  bool LoggerSpdlog::shouldLog(Level level) const {
    return config_->log_level <= level;
  }
}  // namespace logger
