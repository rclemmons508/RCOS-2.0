package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.CompanyProfileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDialog(
    currentProfile: CompanyProfileEntity?,
    onDismiss: () -> Unit,
    onSaveProfile: (company: String, industry: String, bottleneck: String, targetPct: Int, agents: String, customInstructions: String) -> Unit
) {
    var companyName by remember { mutableStateOf(currentProfile?.companyName ?: "Acme Enterprise") }
    var industry by remember { mutableStateOf(currentProfile?.industry ?: "Cross-Industry Workload") }
    var bottleneck by remember { mutableStateOf(currentProfile?.primaryBottleneck?.takeIf { it.isNotBlank() } ?: "Executive Reporting & Task Dispatch") }
    var targetReduction by remember { mutableFloatStateOf((currentProfile?.targetReductionPercent ?: 45).toFloat()) }
    var customInstructions by remember { mutableStateOf(currentProfile?.customInstructions ?: "Automate executive reporting, triage incoming document streams, and optimize inter-departmental task allocation.") }

    var agentSynthesizer by remember { mutableStateOf(true) }
    var agentOnboarding by remember { mutableStateOf(true) }
    var agentAutomator by remember { mutableStateOf(true) }
    var agentAnalyst by remember { mutableStateOf(true) }

    var expandedIndustry by remember { mutableStateOf(false) }

    val industries = listOf(
        "Technology & Software",
        "Financial Services & Banking",
        "Healthcare & Life Sciences",
        "Logistics & Supply Chain",
        "Manufacturing & Industrial",
        "Retail & E-commerce",
        "Professional Services & Legal",
        "Cross-Industry Workload"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    RoundedCornerShape(24.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RCOS System Onboarding Wizard",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Customize individual customer RCOS multi-agent setup before installation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_onboarding_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Onboarding Art Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        AsyncImage(
                            model = R.drawable.img_dashboard_hero,
                            contentDescription = "RCOS Onboarding Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Company Name
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Customer Company Name") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_company_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Industry Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedIndustry,
                        onExpandedChange = { expandedIndustry = !expandedIndustry }
                    ) {
                        OutlinedTextField(
                            value = industry,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Industry / Operations Domain") },
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIndustry) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("onboarding_industry_dropdown"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedIndustry,
                            onDismissRequest = { expandedIndustry = false }
                        ) {
                            industries.forEach { ind ->
                                DropdownMenuItem(
                                    text = { Text(ind) },
                                    onClick = {
                                        industry = ind
                                        expandedIndustry = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary Workload Bottleneck
                    OutlinedTextField(
                        value = bottleneck,
                        onValueChange = { bottleneck = it },
                        label = { Text("Primary Workload Bottleneck") },
                        placeholder = { Text("e.g. Executive Reporting, Document Processing, Triage") },
                        leadingIcon = { Icon(Icons.Default.HourglassTop, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_bottleneck_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Workload Reduction Target Slider
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target Workload Automation Goal",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${targetReduction.toInt()}% Automated",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Slider(
                                value = targetReduction,
                                onValueChange = { targetReduction = it },
                                valueRange = 10f..90f,
                                steps = 7,
                                modifier = Modifier.testTag("onboarding_target_slider")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Active Sub-Agents Selection
                    Text(
                        text = "Enable RCOS Sub-Agents for this Deployment:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    AgentCheckboxRow(
                        title = "Workload Synthesizer Agent",
                        subtitle = "Analyzes corporate tasks & streamlines document overhead",
                        checked = agentSynthesizer,
                        onCheckedChange = { agentSynthesizer = it }
                    )
                    AgentCheckboxRow(
                        title = "Onboarding & Customization Specialist",
                        subtitle = "Adapts RCOS to new company departments & workflows",
                        checked = agentOnboarding,
                        onCheckedChange = { agentOnboarding = it }
                    )
                    AgentCheckboxRow(
                        title = "Workflow Automation & Scripting Agent",
                        subtitle = "Generates scripts, APIs, & automated task pipelines",
                        checked = agentAutomator,
                        onCheckedChange = { agentAutomator = it }
                    )
                    AgentCheckboxRow(
                        title = "Strategic Enterprise Analyst",
                        subtitle = "Provides C-level briefs & strategic decision intelligence",
                        checked = agentAnalyst,
                        onCheckedChange = { agentAnalyst = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom Instructions / Corporate Rules
                    OutlinedTextField(
                        value = customInstructions,
                        onValueChange = { customInstructions = it },
                        label = { Text("Custom Corporate Directives & Agent Rules") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_custom_instructions_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_onboarding_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val selectedAgents = mutableListOf<String>()
                            if (agentSynthesizer) selectedAgents.add("Workload Synthesizer")
                            if (agentOnboarding) selectedAgents.add("Onboarding Specialist")
                            if (agentAutomator) selectedAgents.add("Workflow Automator")
                            if (agentAnalyst) selectedAgents.add("Strategic Analyst")

                            onSaveProfile(
                                companyName,
                                industry,
                                bottleneck,
                                targetReduction.toInt(),
                                selectedAgents.joinToString(", "),
                                customInstructions
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_onboarding_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Apply Onboarding Profile")
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentCheckboxRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
