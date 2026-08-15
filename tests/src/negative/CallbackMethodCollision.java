package negative;

import io.github.jdkbuilder.Buildable;

@Buildable(callbackMethod = "childUsing")
record CallbackMethodCollision(CallbackMethodChild child) {
}

@Buildable
record CallbackMethodChild(String value) {
}
