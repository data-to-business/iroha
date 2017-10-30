/**
 * Copyright Soramitsu Co., Ltd. 2017 All Rights Reserved.
 * http://soramitsu.co.jp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef IROHA_SHARED_MODEL_GET_ASSET_INFO_HPP
#define IROHA_SHARED_MODEL_GET_ASSET_INFO_HPP

#include "interfaces/common_objects/types.hpp"
#include "interfaces/hashable.hpp"
#include "model/queries/get_asset_info.hpp"

namespace shared_model {
  namespace interface {
    class GetAssetInfo
        : public Hashable<GetAssetInfo, iroha::model::GetAssetInfo> {
     public:
      virtual const types::AssetIdType &assetId() const = 0;

      virtual OldModelType *makeOldModel() const override {
        auto oldModel = new iroha::model::GetAssetInfo;
        oldModel->asset_id = assetId();
        return oldModel;
      }

      virtual std::string toString() const override {
        // TODO 30/10/2017 kamilsa implement to string with builders
        return Primitive::toString();
      }
    };
  }  // namespace interface
}  // namespace shared_model

#endif  // IROHA_SHARED_MODEL_GET_ASSET_INFO_HPP
