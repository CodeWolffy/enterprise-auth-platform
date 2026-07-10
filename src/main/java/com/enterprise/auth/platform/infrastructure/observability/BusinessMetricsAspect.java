package com.enterprise.auth.platform.infrastructure.observability;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.observability.PlatformMetrics;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class BusinessMetricsAspect {

    private final PlatformMetrics metrics;

    public BusinessMetricsAspect(PlatformMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("execution(* com.enterprise.auth.platform.modules.auth.application.LoginApplicationService.login(..))")
    public Object login(ProceedingJoinPoint point) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = point.proceed();
            metrics.recordLogin("success", "none", System.nanoTime() - start);
            return result;
        } catch (Throwable exception) {
            metrics.recordLogin("failure", reason(exception), System.nanoTime() - start);
            throw exception;
        }
    }

    @Around("execution(public * com.enterprise.auth.platform.modules.workflow.application.WorkflowApplicationService.*(..))"
            + " || execution(public * com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskUrgeService.urge(..))")
    public Object workflow(ProceedingJoinPoint point) throws Throwable {
        String action = workflowAction(point.getSignature().getName());
        if (action == null) {
            return point.proceed();
        }
        long start = System.nanoTime();
        try {
            Object result = point.proceed();
            metrics.recordWorkflowAction(action, "success", System.nanoTime() - start);
            return result;
        } catch (Throwable exception) {
            metrics.recordWorkflowAction(action, "failure", System.nanoTime() - start);
            throw exception;
        }
    }

    @Around("execution(* com.enterprise.auth.platform.common.web.GlobalExceptionHandler.handle*(..))")
    public Object authorizationFailure(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        if (result instanceof ResponseEntity<?> response) {
            int status = response.getStatusCode().value();
            if (status == 401 || status == 403) {
                metrics.recordAuthorizationDenied(String.valueOf(status), handlerReason(point.getArgs()));
            }
        }
        return result;
    }

    private String handlerReason(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof Throwable exception)) {
            return "unknown";
        }
        return reason(exception);
    }

    private String reason(Throwable exception) {
        return exception instanceof BusinessException businessException
                ? businessException.code()
                : exception.getClass().getSimpleName();
    }

    private String workflowAction(String methodName) {
        return switch (methodName) {
            case "createDefinition" -> "definition_create";
            case "deployDefinition" -> "definition_deploy";
            case "disableDefinition" -> "definition_disable";
            case "startInstance" -> "instance_start";
            case "withdrawInstance" -> "instance_withdraw";
            case "terminateInstance" -> "instance_terminate";
            case "approveTask" -> "task_approve";
            case "rejectTask" -> "task_reject";
            case "transferTask" -> "task_transfer";
            case "urge" -> "task_urge";
            default -> null;
        };
    }
}
