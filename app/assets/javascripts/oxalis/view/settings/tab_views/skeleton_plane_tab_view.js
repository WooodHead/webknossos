import AbstractTabView from "oxalis/view/abstract_tab_view";
import SkeletonTracingSettingsView from "../settings_views/skeleton_tracing_settings_view";
import PlaneUserSettingsView from "../settings_views/plane_user_settings_view";
import DatasetSettingsView from "../settings_views/dataset_settings_view";
import BackboneToOxalisAdapterModel from "oxalis/model/settings/backbone_to_oxalis_adapter_model";

class SkeletonPlaneTabView extends AbstractTabView {

  getTabs() {
    return [
      {
        id : "tracing-settings-tab",
        name : "Tracing",
        iconClass : "fa fa-cogs",
        viewClass : SkeletonTracingSettingsView,
        options : { model: this.adapterModel}
      },
      {
        id : "dataset-settings-tab",
        name : "Dataset",
        iconClass : "fa fa-cogs",
        active : true,
        viewClass : DatasetSettingsView
      },
      {
        id : "user-settings-tab",
        name : "User",
        iconClass : "fa fa-cogs",
        viewClass : PlaneUserSettingsView
      }
    ];
  }
}

export default SkeletonPlaneTabView;