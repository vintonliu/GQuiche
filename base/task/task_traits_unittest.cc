// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/task_traits.h"

#include "testing/gtest/include/gtest/gtest.h"

namespace base {

TEST(TaskTraitsTest, Default) {
  constexpr TaskTraits traits = {};
  EXPECT_EQ(TaskPriority::USER_BLOCKING, traits.priority());
  EXPECT_FALSE(traits.shutdown_behavior_set_explicitly());
  EXPECT_EQ(TaskShutdownBehavior::SKIP_ON_SHUTDOWN, traits.shutdown_behavior());
  EXPECT_FALSE(traits.thread_policy_set_explicitly());
  EXPECT_EQ(ThreadPolicy::PREFER_BACKGROUND, traits.thread_policy());
  EXPECT_FALSE(traits.may_block());
  EXPECT_FALSE(traits.with_base_sync_primitives());
}

TEST(TaskTraitsTest, TaskPriority) {
  constexpr TaskTraits traits = {TaskPriority::BEST_EFFORT};
  EXPECT_EQ(TaskPriority::BEST_EFFORT, traits.priority());
  EXPECT_FALSE(traits.shutdown_behavior_set_explicitly());
  EXPECT_EQ(TaskShutdownBehavior::SKIP_ON_SHUTDOWN, traits.shutdown_behavior());
  EXPECT_FALSE(traits.thread_policy_set_explicitly());
  EXPECT_EQ(ThreadPolicy::PREFER_BACKGROUND, traits.thread_policy());
  EXPECT_FALSE(traits.may_block());
  EXPECT_FALSE(traits.with_base_sync_primitives());
}

TEST(TaskTraitsTest, TaskShutdownBehavior) {
  constexpr TaskTraits traits = {TaskShutdownBehavior::BLOCK_SHUTDOWN};
  EXPECT_EQ(TaskPriority::USER_BLOCKING, traits.priority());
  EXPECT_TRUE(traits.shutdown_behavior_set_explicitly());
  EXPECT_EQ(TaskShutdownBehavior::BLOCK_SHUTDOWN, traits.shutdown_behavior());
  EXPECT_FALSE(traits.thread_policy_set_explicitly());
  EXPECT_EQ(ThreadPolicy::PREFER_BACKGROUND, traits.thread_policy());
  EXPECT_FALSE(traits.may_block());
  EXPECT_FALSE(traits.with_base_sync_primitives());
}

TEST(TaskTraitsTest, ThreadPolicy) {
  constexpr TaskTraits traits = {ThreadPolicy::MUST_USE_FOREGROUND};
  EXPECT_EQ(TaskPriority::USER_BLOCKING, traits.priority());
  EXPECT_FALSE(traits.shutdown_behavior_set_explicitly());
  EXPECT_EQ(TaskShutdownBehavior::SKIP_ON_SHUTDOWN, traits.shutdown_behavior());
  EXPECT_TRUE(traits.thread_policy_set_explicitly());
  EXPECT_EQ(ThreadPolicy::MUST_USE_FOREGROUND, traits.thread_policy());
  EXPECT_FALSE(traits.may_block());
  EXPECT_FALSE(traits.with_base_sync_primitives());
}

TEST(TaskTraitsTest, MayBlock) {
  constexpr TaskTraits traits = {MayBlock()};
  EXPECT_EQ(TaskPriority::USER_BLOCKING, traits.priority());
  EXPECT_FALSE(traits.shutdown_behavior_set_explicitly());
  EXPECT_EQ(TaskShutdownBehavior::SKIP_ON_SHUTDOWN, traits.shutdown_behavior());
  EXPECT_FALSE(traits.thread_policy_set_explicitly());
  EXPECT_EQ(ThreadPolicy::PREFER_BACKGROUND, traits.thread_policy());
  EXPECT_TRUE(traits.may_block());
  EXPECT_FALSE(traits.with_base_sync_primitives());
}

TEST(TaskTraitsTest, WithBaseSyncPrimitives) {
  constexpr TaskTraits traits = {WithBaseSyncPrimitives()};
  EXPECT_EQ(TaskPriority::USER_BLOCKING, traits.priority());
  EXPECT_FALSE(traits.shutdown_behavior_set_explicitly());
  EXPECT_EQ(TaskShutdownBehavior::SKIP_ON_SHUTDOWN, traits.shutdown_behavior());
  EXPECT_FALSE(traits.thread_policy_set_explicitly());
  EXPECT_EQ(ThreadPolicy::PREFER_BACKGROUND, traits.thread_policy());
  EXPECT_FALSE(traits.may_block());
  EXPECT_TRUE(traits.with_base_sync_primitives());
}

TEST(TaskTraitsTest, UpdatePriority) {
  {
    TaskTraits traits = {};
    traits.UpdatePriority(TaskPriority::BEST_EFFORT);
    EXPECT_EQ(TaskPriority::BEST_EFFORT, traits.priority());
  }

  {
    TaskTraits traits = {TaskPriority::USER_VISIBLE};
    traits.UpdatePriority(TaskPriority::BEST_EFFORT);
    EXPECT_EQ(TaskPriority::BEST_EFFORT, traits.priority());
  }
}

TEST(TaskTraitsTest, MultipleTraits) {
  constexpr TaskTraits traits = {
      TaskPriority::BEST_EFFORT, TaskShutdownBehavior::BLOCK_SHUTDOWN,
      ThreadPolicy::MUST_USE_FOREGROUND, MayBlock(), WithBaseSyncPrimitives()};
  EXPECT_EQ(TaskPriority::BEST_EFFORT, traits.priority());
  EXPECT_TRUE(traits.shutdown_behavior_set_explicitly());
  EXPECT_EQ(TaskShutdownBehavior::BLOCK_SHUTDOWN, traits.shutdown_behavior());
  EXPECT_TRUE(traits.thread_policy_set_explicitly());
  EXPECT_EQ(ThreadPolicy::MUST_USE_FOREGROUND, traits.thread_policy());
  EXPECT_TRUE(traits.may_block());
  EXPECT_TRUE(traits.with_base_sync_primitives());
}

TEST(TaskTraitsTest, Copy) {
  constexpr TaskTraits traits = {
      TaskPriority::BEST_EFFORT, TaskShutdownBehavior::BLOCK_SHUTDOWN,
      ThreadPolicy::MUST_USE_FOREGROUND, MayBlock(), WithBaseSyncPrimitives()};
  constexpr TaskTraits traits_copy(traits);

  EXPECT_EQ(traits, traits_copy);

  EXPECT_EQ(traits.priority(), traits_copy.priority());
  EXPECT_EQ(traits.shutdown_behavior_set_explicitly(),
            traits_copy.shutdown_behavior_set_explicitly());
  EXPECT_EQ(traits.shutdown_behavior(), traits_copy.shutdown_behavior());
  EXPECT_EQ(traits.thread_policy_set_explicitly(),
            traits_copy.thread_policy_set_explicitly());
  EXPECT_EQ(traits.thread_policy(), traits_copy.thread_policy());
  EXPECT_EQ(traits.may_block(), traits_copy.may_block());
  EXPECT_EQ(traits.with_base_sync_primitives(),
            traits_copy.with_base_sync_primitives());
}

}  // namespace base
